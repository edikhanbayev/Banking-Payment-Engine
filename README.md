# Open Banking: Double-Entry Ledger and Payment Engine

A backend project for financial ledgering and payment processing, built with **Java 21, Spring Boot, PostgreSQL, Redis, and Apache Kafka**.

The project focuses on concerns that simple CRUD applications usually do not address: **double-entry accounting, immutable financial history, concurrent debiting, API idempotency, transaction boundaries, asynchronous event delivery, and auditability**.

The long-term goal is to build a payment engine oriented toward **Kazakhstan Open Banking** use cases. The current implementation **is not yet a complete implementation of the Kazakhstan Open Banking specification**.

> **Project status:** active development. The core financial ledger, concurrent internal transfers, idempotency, Transactional Outbox, Kafka event publishing, and payment lifecycle have been implemented.

---

## Architecture

```text
    API Client
        |
        | POST transfer + Idempotency-Key
        v
    Spring Boot Payment API
        |
        v
    Redis
    idempotency cache
        |
        +--> cache hit --> return stored response
        |
        +--> miss
              |
              v
          PostgreSQL
          idempotency
              |
              v
          Account locking
          SELECT FOR UPDATE
          ORDER BY account_id
              |
              v
          Account validation
          and available-funds check
              |
              v
          Payment lifecycle
          INITIATED -> PROCESSING -> POSTED
              |
              v
          Double-entry ledger
              |
              +--> immutable journal_entries and postings
              |
              +--> account_state
              |    current-balance projection
              |
              v
          Transactional Outbox
              |
              v
          Background publishing
              |
              v
          Apache Kafka
          payment.events
```

The key architectural rule of the project is:

> **PostgreSQL is the boundary of financial correctness.**

Redis and Kafka improve performance and provide integration capabilities, but they are not used as the source of truth for monetary operations.

---

# Current Technology Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Build | Maven Wrapper |
| Database | PostgreSQL 18 |
| Database access | Spring JDBC / `JdbcTemplate` |
| Database migrations | Flyway |
| Cache | Redis 8 |
| Messaging | Apache Kafka 4.1 |
| JSON | Jackson |
| Local infrastructure | Docker Compose |
| Testing | JUnit 5 |

The financial-ledger code intentionally uses explicit SQL through `JdbcTemplate` rather than hiding critical financial operations behind ORM abstractions.

---

# Core Financial Model

The system does not use a single mutable `balance` column as the financial source of truth.

Financial history is represented as follows:

```text
ledger_accounts
      |
      v
journal_entries
      |
      v
postings
```

A transfer of 10,000 KZT from customer account A to customer account B creates an accounting entry like this:

```text
Journal: internal transfer

DEBIT   Liability to customer A    10,000 KZT
CREDIT  Liability to customer B    10,000 KZT
                                  ------------
Total debit                       10,000 KZT
Total credit                      10,000 KZT
```

Every journal must satisfy:

```text
Σ Debit = Σ Credit
```

The validation is performed separately for each currency.

For example, the following journal is rejected:

```text
DEBIT   100 USD
CREDIT  100 KZT
```

even though the numerical amounts are equal.

---

# Monetary Value Representation

Monetary amounts are stored in minor currency units as integers:

```java
long amountMinor;
```

instead of floating-point types.

Example:

```text
10,000.50 KZT
=
1,000,050 tiyn
```

In the database:

```sql
amount_minor BIGINT
```

This avoids rounding errors associated with floating-point numbers.

---

# Dual Protection Against Unbalanced Entries

Accounting-data integrity is enforced at two levels.

### Application Level

`JournalValidator` checks that the total debits equal the total credits before the journal is persisted.

### PostgreSQL Level

A deferred constraint trigger in PostgreSQL independently validates the journal before the transaction is committed.

Conceptually:

```text
BEGIN

INSERT journal
INSERT debit
INSERT credit

COMMIT
   |
   v
PostgreSQL verifies:
Debit == Credit
```

This means that even if a future Java bug bypasses the application-level validation, an unbalanced journal should still not be committed to the database.

---

# Immutable Financial Ledger

Committed financial history is stored using an append-only approach.

PostgreSQL triggers prohibit:

```sql
UPDATE postings ...
DELETE FROM postings ...
TRUNCATE postings;
```

and equivalent modifications to `journal_entries`.

Financial errors should be corrected not by modifying historical entries, but by creating a reversing entry.

Example:

```text
Original transaction:

DEBIT  A    10,000
CREDIT B    10,000


Reversal:

DEBIT  B    10,000
CREDIT A    10,000
```

The original accounting entry remains preserved for audit purposes.

---

# Balance Projection

Reading millions of postings every time a balance is requested would be inefficient.

The project therefore maintains:

```text
account_state
```

with the following fields:

```text
posted_balance_minor
available_balance_minor
version
updated_at
```

It is important to distinguish between:

```text
postings
=
financial source of truth
```

and:

```text
account_state
=
derived projection of current state
```

The projection is updated inside the same PostgreSQL transaction that writes to the financial ledger.

A separate reconciliation process is planned to recompute balances from the immutable ledger and compare them with `account_state`.

---

# Internal Transfer API

The current payment endpoint is:

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

The UUID values above are examples only.

---

# Atomic Payment Transaction

A successful transfer is conceptually executed as follows:

```text
BEGIN

1. Acquire or validate Idempotency-Key

2. Lock both account_state rows

3. Validate:
   - accounts exist
   - accounts are in OPEN status
   - currencies match
   - sufficient available funds exist

4. INSERT payment
   INITIATED

5. Transition:
   INITIATED -> PROCESSING

6. INSERT journal_entry

7. INSERT debtor DEBIT posting

8. UPDATE debtor account_state

9. INSERT creditor CREDIT posting

10. UPDATE creditor account_state

11. Transition:
    PROCESSING -> POSTED

12. INSERT PaymentPosted outbox event

13. Mark idempotent request as COMPLETED

COMMIT

14. Store idempotent response in Redis
```

If a database operation fails before `COMMIT`, the entire PostgreSQL transaction is rolled back.

---

# Protection Against Double Spending Under Concurrent Requests

The project uses PostgreSQL pessimistic row locking:

```sql
SELECT ...
FROM account_state
WHERE account_id IN (?, ?)
ORDER BY account_id
FOR UPDATE OF account_state;
```

Consider this situation:

```text
Available balance = 1,000 KZT

Request A = debit 800 KZT
Request B = debit 800 KZT
```

Without locking, both requests could read a 1,000 KZT balance at the same time and both might succeed.

With row locking:

```text
Transaction A
    |
    +-- locks the account
    +-- sees 1,000
    +-- debits 800
    +-- commits balance = 200

Transaction B
    |
    +-- waits for the lock
    +-- acquires the lock
    +-- sees 200
    +-- is rejected: insufficient funds
```

PostgreSQL, not Redis, provides the final protection against concurrent overspending.

---

# Lock Ordering and Deadlock-Risk Reduction

Transfers may execute simultaneously in opposite directions:

```text
A -> B
B -> A
```

If the system locks `fromAccount` first and `toAccount` second, this can occur:

```text
Transaction 1 locks A -> waits for B
Transaction 2 locks B -> waits for A
```

The system therefore locks both account rows in deterministic order:

```sql
ORDER BY account_id
FOR UPDATE;
```

This ensures both transfer directions acquire locks in the same canonical order.

It reduces the risk of an important class of deadlocks, although it does not imply that every possible deadlock scenario is permanently eliminated.

---

# Strict API Idempotency

Every payment request must contain:

```text
Idempotency-Key
```

The primary idempotency state is stored in PostgreSQL:

```text
api_idempotency
```

Unique key:

```text
(client_id, idempotency_key)
```

The system also calculates a SHA-256 hash of the canonical representation of the transfer request.

This supports three main scenarios.

### New Request

```text
New key
+
new payload
->
execute transfer
```

### Request Retry

```text
Same key
+
same payload
->
return original result
->
do not create a second financial effect
```

### Invalid Key Reuse

```text
Same key
+
different payload
->
HTTP 409 Conflict
```

This protects the API against network retries, for example:

```text
Server commits payment
        |
        v
HTTP response is lost
        |
        v
Client retries request
        |
        v
Original payment result is returned
```

instead of executing the monetary operation a second time.

---

# Redis Usage

Redis is used only as an **L1 cache for idempotency responses**.

Flow:

```text
Request
  |
  v
Redis
  |
  +-- hit -> return stored result
  |
  +-- miss
        |
        v
     PostgreSQL
```

A Redis failure is treated as a cache failure.

The application falls back to PostgreSQL rather than making Redis availability a requirement for safe payment execution.

A successful response is written to Redis only after the PostgreSQL transaction commits. This prevents Redis from reporting a successful payment before the financial operation has been durably persisted.

---

# Transactional Outbox

Sending a Kafka message directly after committing the database transaction creates a failure window:

```text
PostgreSQL COMMIT succeeds
        |
        v
Application crashes
        |
        v
Kafka message is never sent
```

The project addresses this using:

```text
outbox_events
```

The financial transaction writes the event to PostgreSQL inside the same transaction:

```text
BEGIN

payment
ledger
balance projection
idempotency result
outbox event

COMMIT
```

Kafka publishing happens separately.

This means every committed payment leaves behind a durably stored event waiting to be published.

---

# Kafka Event Publishing

A background process reads unpublished events:

```sql
SELECT ...
FROM outbox_events
WHERE published_at IS NULL
ORDER BY created_at, id
FOR UPDATE SKIP LOCKED;
```

`SKIP LOCKED` allows multiple workers to claim different outbox records without waiting on rows already locked by another worker.

Events are published to:

```text
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

After Kafka confirms publication, the following field is written to the outbox row:

```text
published_at
```

and the attempt counter is updated.

---

# Behavior When Kafka Is Unavailable

Kafka is intentionally outside the synchronous financial transaction.

If Kafka is unavailable:

```text
Payment               ✅ committed
Financial ledger       ✅ committed
Balances               ✅ committed
Idempotency            ✅ committed
Outbox event           ✅ committed
Kafka publication      ❌ temporarily unavailable
```

After Kafka recovers, the background process retries unpublished events.

Therefore, Kafka availability does not determine the correctness of the financial ledger.

---

# Delivery Semantics

The current event-delivery model is:

```text
at-least-once
```

not:

```text
exactly-once
```

For example:

```text
Kafka receives event
        |
        v
Application crashes before published_at is updated
        |
        v
Background worker starts again
        |
        v
Event may be published again
```

Future Kafka consumers must therefore deduplicate events using:

```text
eventId
```

An idempotent consumer / inbox pattern has not yet been implemented.

---

# Payment Lifecycle

Payment business state is stored separately from accounting history.

Current statuses:

```text
INITIATED
AUTHORIZED
PROCESSING
POSTED
FAILED
REVERSED
```

Internal transfers typically move through:

```text
INITIATED
    |
    v
PROCESSING
    |
    v
POSTED
```

Transitions are recorded in:

```text
payment_status_history
```

Example:

```text
NULL        -> INITIATED
INITIATED   -> PROCESSING
PROCESSING  -> POSTED
```

This separation is intentional:

```text
payments
=
payment business process
```

whereas:

```text
journal_entries + postings
=
financial accounting truth
```

The financial ledger is immutable, while payment-process state may change.

---

# Current Database Schema

Main implemented tables:

| Table | Purpose |
|---|---|
| `ledger_accounts` | Financial accounts |
| `journal_entries` | Accounting journal headers |
| `postings` | Immutable debit and credit postings |
| `account_state` | Current-balance projection |
| `payments` | Payment lifecycle |
| `payment_status_history` | Payment status history |
| `api_idempotency` | Durable HTTP API idempotency |
| `outbox_events` | Transactional Outbox for Kafka |
| `flyway_schema_history` | Database migration history |

---

# Database Migrations

Current migrations:

```text
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

All database-schema changes are performed exclusively through Flyway migrations.

---

# Local Setup

## Requirements

Install:

```text
Java 21
Docker Desktop
Git
```

A separate Maven installation is not required because the repository includes Maven Wrapper.

---

## Start Infrastructure

From the project root:

```powershell
docker compose up -d
```

Check:

```powershell
docker compose ps
```

The current environment should run:

```text
PostgreSQL
Redis
Kafka
```

---

## Start the Application

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The API will be available at:

```text
http://localhost:8080
```

Flyway migrations run automatically on startup.

---

## Run Tests

```powershell
.\mvnw.cmd test
```

---

# Demo Accounts

Development migrations create demonstration KZT accounts, for example:

```text
Customer A
11111111-1111-1111-1111-111111111111

Customer B
22222222-2222-2222-2222-222222222222

Bank Cash
99999999-9999-9999-9999-999999999999
```

The existence of an account does not imply that it has a balance.

A balance is created by accounting postings.

Temporary development endpoint:

```http
POST /internal/ledger/journals
```

can be used to create initial funding postings.

This endpoint is intended only for development and testing and should not be exposed as a public banking API in production.

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

Check events waiting for publication:

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

The current version provides:

- double-entry accounting;
- debit/credit validation at the Java level;
- independent accounting-correctness validation at the PostgreSQL level;
- posting balance validation separately for each currency;
- immutability of `journal_entries` and `postings`;
- storage of monetary values in integer minor units;
- atomic updates across payments, financial ledger, and balances;
- a separate current-balance projection;
- sufficient-funds checks;
- PostgreSQL pessimistic row locking;
- deterministic account-lock ordering;
- protection against concurrent double spending;
- durable API idempotency in PostgreSQL;
- SHA-256 fingerprinting of request content;
- return of the original result for safe retries;
- detection of reuse of the same `Idempotency-Key` with different request data;
- Redis caching of idempotent responses with PostgreSQL fallback;
- Transactional Outbox;
- background Kafka event publishing;
- retryable event delivery;
- explicitly accepted `at-least-once` semantics;
- payment lifecycle management;
- payment-status history.

---

# Current Limitations

The repository is intentionally **not described as production-ready or bank-grade**.

The following have not yet been implemented:

```text
Funds reservation

External payment settlement

Idempotent Kafka consumers / inbox pattern

Dead-letter queue

Automated reconciliation

OAuth2 / OpenID Connect

Customer consent management

Kazakhstan Open Banking API adapters

ISO 20022 integration

Security-event audit log

Rate limiting

Prometheus metrics

Grafana dashboards

Load testing with k6

Integration tests with Testcontainers

Automated CI/CD pipeline

Secrets management

Production-grade PostgreSQL roles and permissions

TLS / mTLS

Retry/backoff policies

Disaster Recovery strategy
```

The project is therefore more accurately described as:

> **an engineering project for a payment engine and financial ledger oriented toward Kazakhstan Open Banking use cases**

rather than:

> **a fully Kazakhstan Open Banking-compliant system**

because the formal external Open Banking APIs, security model, and consent management have not yet been implemented.

---

# Planned Next Steps

The next development stages are:

```text
1. Funds reservation and available-balance holds

2. Kazakhstan Open Banking adapter architecture boundary

3. Account Information APIs

4. OAuth2, JWT, and consent management

5. Immutable security-event audit log

6. Observability, reconciliation, integration testing,
   load testing, and production-readiness work
```

---

# Engineering Principles

The project is built around the following principles:

```text
PostgreSQL is responsible for financial correctness.

The financial ledger is immutable.

Balance is derived state, not financial history.

Monetary values are stored as integer minor units.

Every accounting journal must be balanced.

Account locks are acquired in deterministic order.

A network retry must not create a duplicate payment.

Redis is an optimization, not the source of truth.

Kafka failure must not lose events for already committed payments.

Kafka delivery is treated as at-least-once.

Payment business state and accounting history are different concepts.

Architectural claims should be supported by tests,
not only by diagrams.
```

---

# Project Goal

The purpose of this repository is not to simulate a complete commercial bank.

The project is intended to demonstrate engineering approaches common in financial and payment systems:

```text
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

The project will continue evolving toward Kazakhstan Open Banking use cases while keeping the financial ledger independent of external API protocols.
