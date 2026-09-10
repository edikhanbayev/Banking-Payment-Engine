# Open Banking Double-Entry Ledger & Payment Engine

A backend financial ledger and payment-processing project built with **Java 21, Spring Boot, PostgreSQL, Redis, and Apache Kafka**.

The project focuses on problems that simple CRUD banking applications usually ignore: **double-entry accounting, immutable financial history, concurrent spending, idempotent API requests, transaction boundaries, asynchronous event delivery, and auditability**.

The long-term goal is to build a payment engine aligned with Kazakhstan Open Banking use cases. The current implementation is **not yet an implementation of the full Kazakhstan Open Banking specification**.

> Project status: Active development — core ledger, concurrent internal transfers, idempotency, transactional outbox, Kafka publishing, and payment lifecycle are implemented.

---

## Architecture

```
    Client[API Client]

    Client -->|POST transfer<br/>Idempotency-Key| API[Spring Boot Payment API]

    API --> IC{Redis<br/>Idempotency Cache}

    IC -->|Cache miss| IDB[(PostgreSQL<br/>Idempotency)]
    IC -->|Cache hit| API

    IDB --> LOCK[Account Locking<br/>SELECT FOR UPDATE<br/>ORDER BY account_id]

    LOCK --> CHECK[Funds & Account Validation]

    CHECK --> PAYMENT[Payment Lifecycle<br/>INITIATED → PROCESSING → POSTED]

    PAYMENT --> LEDGER[Double-Entry Ledger]

    LEDGER --> POSTINGS[(Immutable<br/>Journal Entries & Postings)]

    LEDGER --> STATE[(Account State<br/>Balance Projection)]

    PAYMENT --> OUTBOX[(Transactional Outbox)]

    OUTBOX --> RELAY[Background Outbox Relay]

    RELAY --> KAFKA[Apache Kafka<br/>payment.events]

    API -. after commit .-> IC
```

The main architectural rule is:

-PostgreSQL is the financial correctness boundary-

Redis and Kafka improve performance and integration, but neither is trusted as the source of truth for money.

---

# Current Technology Stack

| Component            | Technology                   |
| -------------------- | ---------------------------- |
| Language             | Java 21                      |
| Framework            | Spring Boot 4.1.1            |
| Build                | Maven Wrapper                |
| Database             | PostgreSQL 18                |
| Database access      | Spring JDBC / `JdbcTemplate` |
| Database migrations  | Flyway                       |
| Cache                | Redis 8                      |
| Messaging            | Apache Kafka 4.1             |
| JSON                 | Jackson                      |
| Local infrastructure | Docker Compose               |
| Testing              | JUnit 5                      |

The ledger code intentionally uses explicit SQL through `JdbcTemplate` rather than hiding financial operations behind ORM abstractions.

---

# Core Financial Model

The system does not use a single mutable `balance` column as the financial source of truth.

Financial history is represented using:

```
ledger_accounts
       │
       ▼
journal_entries
       │
       ▼
postings
```

A transfer of 10,000 KZT from Customer A to Customer B creates an accounting journal similar to:

```
Journal: Internal Transfer

DEBIT   Customer A Liability    10,000 KZT
CREDIT  Customer B Liability    10,000 KZT
                                ----------
Total Debits                    10,000 KZT
Total Credits                   10,000 KZT
```

Every journal must satisfy:

``` 
Σ Debits = Σ Credits
```

The invariant is checked independently for each currency.

For example, this is rejected:

```  
DEBIT   100 USD
CREDIT  100 KZT
```

even though the numeric amounts are equal.

---

# Money Representation

Money is stored using integer minor units:

```
long amountMinor;
```

instead of floating-point values.

Example:

```
10,000.50 KZT
=
1,000,050 tiyn
```

Database representation:

```
amount_minor BIGINT
```

This avoids floating-point rounding errors in financial calculations.

---

# Double Protection Against Unbalanced Journals

Accounting integrity is enforced in two places.

### Application layer

`JournalValidator` checks that debit and credit totals balance before writing the journal.

### PostgreSQL layer

A deferred PostgreSQL constraint trigger independently checks the journal before the transaction commits.

Conceptually:

```
BEGIN

INSERT journal
INSERT debit
INSERT credit

COMMIT
   │
   ▼
PostgreSQL verifies:
Debit == Credit
```

This means a future Java bug that bypasses application validation should still not be able to commit an unbalanced journal.

---

# Immutable Ledger

Committed financial history is append-only.

PostgreSQL triggers reject:

```
UPDATE postings ...
DELETE FROM postings ...
TRUNCATE postings;
```

and equivalent mutations of `journal_entries`.

Financial mistakes should therefore be corrected using **reversal journals**, rather than modifying historical postings.

Example:

```.
Original:

DEBIT  A    10,000
CREDIT B    10,000


Reversal:

DEBIT  B    10,000
CREDIT A    10,000
```

The original accounting record remains available for audit purposes.

---

# Balance Projection

Reading millions of postings every time an account balance is requested would be inefficient.
The project therefore maintains:

```.
account_state
```

containing:

```.
posted_balance_minor
available_balance_minor
version
updated_at
```

The important distinction is:

```.
postings
=
financial source of truth
```

while:

```.
account_state
=
derived current-state projection
```

The projection is updated inside the same PostgreSQL transaction as the ledger posting.

A future reconciliation process will independently recalculate balances from the immutable ledger and compare them with `account_state`.

---

# Internal Transfer API

Current payment endpoint:

```http
POST /api/v1/transfers/internal
```

Example request:

```http
POST /api/v1/transfers/internal
Content-Type: application/json
X-Client-Id: demo-client
Idempotency-Key: payment-test-001
```

```json
{
  "fromAccountId": "11111111-1111-1111-1111-111111111111",
  "toAccountId": "22222222-2222-2222-2222-222222222222",
  "amountMinor": 100000,
  "currency": "KZT"
}
```

Example successful response:

```json
{
  "paymentId": "7ffce65c-c480-43bd-a5c1-d333bb4f4b21",
  "journalEntryId": "a58d5ae2-5847-45cd-a361-a58950335d70",
  "status": "POSTED"
}
```

The UUID values above are examples.

---

# Atomic Payment Transaction

A successful transfer currently executes conceptually as:

```text
BEGIN

1. Claim/check Idempotency-Key

2. Lock both account_state rows

3. Validate:
   - accounts exist
   - accounts are OPEN
   - currencies match
   - available funds are sufficient

4. INSERT payment
   INITIATED

5. Transition:
   INITIATED → PROCESSING

6. INSERT journal_entry

7. INSERT debtor DEBIT posting

8. UPDATE debtor account_state

9. INSERT creditor CREDIT posting

10. UPDATE creditor account_state

11. Transition:
    PROCESSING → POSTED

12. INSERT PaymentPosted outbox event

13. Mark idempotency request COMPLETED

COMMIT

14. Cache idempotent response in Redis
```

If a database operation fails before commit, the PostgreSQL transaction rolls back.

---

# Concurrent Double-Spend Protection

The project uses PostgreSQL row-level pessimistic locking:

```sql
SELECT ...
FROM account_state
WHERE account_id IN (?, ?)
ORDER BY account_id
FOR UPDATE OF account_state;
```

Consider:

```.
Available balance = 1,000 KZT

Request A = spend 800 KZT
Request B = spend 800 KZT
```

Without locking, both requests could read 1,000 and both succeed.

With row-level locking:

```.
Transaction A
    │
    ├── locks account
    ├── sees 1,000
    ├── spends 800
    └── commits balance = 200

Transaction B
    │
    ├── waits
    ├── acquires lock
    ├── sees 200
    └── rejected: insufficient funds
```
PostgreSQL, rather than Redis, provides the final protection against concurrent overspending.
---

# Deadlock-Safe Lock Ordering

Transfers can occur simultaneously in opposite directions:

```.
A → B
B → A
```

Locking `fromAccount` followed by `toAccount` could produce:

```.
Transaction 1 locks A → waits for B
Transaction 2 locks B → waits for A
```

The system instead locks both account rows in deterministic order:

```sql
ORDER BY account_id
FOR UPDATE;
```

Therefore both transfer directions acquire account locks in the same canonical order.

This reduces an important class of database deadlocks.

It does not mean that all future deadlocks are impossible as the system grows.

---

# Strict API Idempotency

Every payment request requires an:

```.
Idempotency-Key
```

Idempotency is primarily stored in PostgreSQL:

```.
api_idempotency
```

The unique key is:

```.
(client_id, idempotency_key)
```

The system also calculates a SHA-256 hash from the canonical transfer request.

This allows three important behaviors.

### New request

```.
New key
+
new payload
→ execute transfer
```

### Retry

```text
Same key
+
same payload
→ return original result
→ no second financial effect
```

### Incorrect key reuse

```.
Same key
+
different payload
→ HTTP 409 Conflict
```

This protects the API from network-retry scenarios such as:

```.
Server commits payment
        ↓
HTTP response is lost
        ↓
Client retries
        ↓
Original payment result returned
```

instead of performing the payment twice.

---

# Redis Usage

Redis acts only as an **L1 idempotency response cache**.

Flow:

```.
Request
   │
   ▼
Redis
   │
   ├── hit → replay result
   │
   └── miss
          │
          ▼
      PostgreSQL
```

Redis failures are handled as cache failures.

The application falls back to PostgreSQL rather than allowing Redis availability to determine whether money can safely move.

Also, successful responses are written to Redis after the PostgreSQL transaction commits, preventing Redis from reporting a payment as successful before the financial transaction is durable.

---

# Transactional Outbox

Sending a Kafka message directly after a database commit creates a failure window:

```.
PostgreSQL COMMIT succeeds
        ↓
application crashes
        ↓
Kafka message never sent
```

The project solves this using:

```.
outbox_events
```

The financial transaction writes the event into PostgreSQL inside the same transaction:

```.
BEGIN

payment
ledger
balance projection
idempotency result
outbox event

COMMIT
```

Kafka publishing happens separately.

This ensures a committed payment always has a durable event waiting to be published.

---

# Kafka Event Publishing

A scheduled background worker reads unpublished events using:

```sql
SELECT ...
FROM outbox_events
WHERE published_at IS NULL
ORDER BY created_at, id
FOR UPDATE SKIP LOCKED;
```

`SKIP LOCKED` allows multiple workers to process different outbox records without waiting on rows already claimed by another worker.

Events are published to:

```.
payment.events
```

Example `PaymentPosted` event:

```json
{
  "eventId": "...",
  "paymentId": "...",
  "journalEntryId": "...",
  "debtorAccountId": "...",
  "creditorAccountId": "...",
  "amountMinor": 100000,
  "currency": "KZT",
  "occurredAt": "..."
}
```

After Kafka acknowledges publication, the outbox row receives:

```.
published_at
```

and its attempt counter is updated.

---

# Kafka Failure Behaviour

Kafka is deliberately outside the synchronous financial transaction.

If Kafka is unavailable:

```.
Payment              ✅ committed
Ledger               ✅ committed
Balances             ✅ committed
Idempotency          ✅ committed
Outbox event         ✅ committed
Kafka publication    ❌ temporarily unavailable
```

When Kafka becomes available again, the outbox relay retries unpublished events.

Therefore Kafka availability does not determine whether the financial ledger remains correct.

---

# Delivery Semantics

The current event-delivery model is:

```.
at-least-once
```

not:
```.
exactly-once
```

For example:

```.
Kafka receives event
        ↓
application crashes before
published_at is updated
        ↓
worker restarts
        ↓
event may be published again
```

Future Kafka consumers must therefore deduplicate events using:

```.
eventId
```

An idempotent consumer/inbox implementation has not yet been added.

---

# Payment Lifecycle

Payment business state is kept separately from accounting history.

Current statuses are:

```.
INITIATED
AUTHORIZED
PROCESSING
POSTED
FAILED
REVERSED
```

Current internal transfers normally execute:

```.
INITIATED
    ↓
PROCESSING
    ↓
POSTED
```

Transitions are recorded in:

```.
payment_status_history
```

Example:

```.
NULL        → INITIATED
INITIATED   → PROCESSING
PROCESSING  → POSTED
```

This separation is deliberate:

```.
payments
=
business workflow
```

while:

```.
journal_entries + postings
=
financial accounting truth
```

The ledger is immutable; payment workflow state may change.

---

# Current Database Schema

The main tables implemented so far are:

| Table                    | Responsibility                   |
| ------------------------ | -------------------------------- |
| `ledger_accounts`        | Financial accounts               |
| `journal_entries`        | Accounting transaction headers   |
| `postings`               | Immutable debit/credit entries   |
| `account_state`          | Current balance projection       |
| `payments`               | Payment workflow                 |
| `payment_status_history` | Payment state transition history |
| `api_idempotency`        | Durable HTTP idempotency         |
| `outbox_events`          | Kafka transactional outbox       |
| `flyway_schema_history`  | Database migration history       |

---

# Database Migrations

Current migrations:

```.
V1__create_ledger_schema.sql
V2__seed_demo_accounts.sql
V3__enforce_balanced_journals.sql
V4__make_ledger_immutable.sql
V5__create_account_state.sql
V6__create_payments.sql
V7__create_idempotency.sql
V8__create_outbox.sql
V9__extend_payment_lifecycle.sql
```

Database schema changes are managed exclusively through Flyway migrations.

---

# Running Locally

## Requirements

Install:

```.
Java 21
Docker Desktop
Git
```

Maven does not need to be installed separately because the repository contains the Maven Wrapper.

---

## Start infrastructure

From the project directory:

```powershell
docker compose up -d
```

Check:

```powershell
docker compose ps
```

The current environment should include:

```.
PostgreSQL
Redis
Kafka
```

---

## Start the application

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The API runs at:

```.
http://localhost:8080
```

Flyway migrations execute automatically during startup.

---

## Run tests

```powershell
.\mvnw.cmd test
```

---

# Demo Accounts

Development migrations currently create demo KZT ledger accounts such as:

```.
Customer A
11111111-1111-1111-1111-111111111111

Customer B
22222222-2222-2222-2222-222222222222

Bank Cash
99999999-9999-9999-9999-999999999999
```

The accounts themselves should not be confused with balances.

Balances originate from accounting postings.

The temporary development endpoint:

```http
POST /internal/ledger/journals
```

can currently be used to create initial funding journals.

This endpoint exists for development and testing and should not be exposed as a public banking API in a production system.

---

# Useful Database Checks

Connect to PostgreSQL:

```powershell
docker exec -it ledger-postgres psql -U ledger -d ledger
```

Check Flyway migrations:

```sql
SELECT
    version,
    description,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Check payment statuses:

```sql
SELECT
    status,
    COUNT(*)
FROM payments
GROUP BY status;
```

Check pending outbox events:

```sql
SELECT
    event_type,
    published_at,
    attempt_count,
    last_error
FROM outbox_events
ORDER BY created_at DESC;
```

Check PostgreSQL deadlocks:

```sql
SELECT
    datname,
    deadlocks
FROM pg_stat_database
WHERE datname = 'ledger';
```

---

# Implemented Guarantees

The current implementation provides:

* Double-entry accounting
* Debit/credit validation in Java
* Independent PostgreSQL accounting validation
* Per-currency journal balancing
* Immutable journal entries and postings
* Integer-based money representation
* Atomic payment + ledger + balance updates
* Current balance projection
* Insufficient-funds checks
* PostgreSQL pessimistic row locking
* Deterministic account lock ordering
* Concurrent double-spend protection
* Durable PostgreSQL API idempotency
* SHA-256 request fingerprinting
* Same-request response replay
* Detection of reused idempotency keys with different payloads
* Redis idempotency caching with PostgreSQL fallback
* Transactional Outbox
* Kafka background event publication
* Retryable Kafka delivery
* Explicit at-least-once delivery semantics
* Payment lifecycle tracking
* Payment status history

---

# Current Limitations

This repository is intentionally not described as production-ready or bank-grade yet.

The following major capabilities are still missing:

```.
Fund holds / reservations

External payment settlement

Idempotent Kafka consumers / inbox pattern

Dead-letter queue strategy

Automated reconciliation

OAuth2 / OpenID Connect authentication

Consent management

Kazakhstan Open Banking API adapters

ISO 20022 integration

Security audit log

Rate limiting

Prometheus metrics

Grafana dashboards

k6 load-test suite

Testcontainers integration tests

Automated CI/CD pipeline

Secrets management

Production database roles and permissions

TLS / mTLS

Operational retry/backoff policies

Disaster recovery strategy
```

In particular, the project should currently be described as:

> **Kazakhstan Open Banking–aligned payment and ledger engineering project**

rather than:

> **Kazakhstan Open Banking compliant system**

because the formal external Open Banking interfaces and security/consent integration have not yet been implemented.

---

# Planned Next Steps

The next development milestones are:

```.
1. Fund holds and available-balance reservations

2. Kazakhstan Open Banking adapter boundary

3. Account Information APIs

4. OAuth2, JWT and consent handling

5. Append-only security audit log

6. Observability, reconciliation, integration testing,
     load testing and production-hardening
```

---

# Engineering Principles

This project follows several deliberate design principles:

```.
PostgreSQL owns financial correctness.

The ledger is immutable.

Balances are derived state, not financial history.

Money uses integer minor units.

Every financial journal balances.

Account locks are deterministic.

Network retries must not duplicate payments.

Redis is an optimization, not the source of truth.

Kafka outages must not lose committed financial events.

Kafka delivery is assumed to be at-least-once.

Business workflow and accounting history are separate concepts.

Claims in documentation should be demonstrated by tests,
not by architecture diagrams alone.
```

---

# Project Goal

The purpose of this repository is not to simulate a complete commercial bank.

It is to demonstrate backend engineering techniques relevant to financial and payment systems:

```.
Accounting invariants
Transaction isolation
Concurrency control
Race-condition prevention
Idempotent APIs
Distributed-system failure handling
Event-driven architecture
Auditability
Database-first correctness
```

Future milestones will extend this foundation toward Kazakhstan Open Banking scenarios while keeping the financial ledger independent from external API protocols.
