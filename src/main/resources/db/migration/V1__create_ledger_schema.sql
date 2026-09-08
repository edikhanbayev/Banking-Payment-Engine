CREATE TABLE ledger_accounts
(
    id              UUID PRIMARY KEY,

    owner_id        UUID,

    account_code    VARCHAR(100) NOT NULL UNIQUE,

    account_type    VARCHAR(20) NOT NULL,

    currency        CHAR(3) NOT NULL,

    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_account_type
        CHECK (
            account_type IN (
                             'ASSET',
                             'LIABILITY',
                             'EQUITY',
                             'REVENUE',
                             'EXPENSE'
                )
            ),

    CONSTRAINT chk_account_status
        CHECK (
            status IN (
                       'OPEN',
                       'FROZEN',
                       'CLOSED'
                )
            ),

    CONSTRAINT chk_account_currency
        CHECK (
            currency ~ '^[A-Z]{3}$'
),

    CONSTRAINT uq_account_id_currency
        UNIQUE (id, currency)
);


CREATE TABLE journal_entries
(
    id              UUID PRIMARY KEY,

    reference_type  VARCHAR(50) NOT NULL,

    reference_id    UUID NOT NULL,

    description     VARCHAR(500),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_journal_reference
        UNIQUE (reference_type, reference_id)
);


CREATE TABLE postings
(
    id                  UUID PRIMARY KEY,

    journal_entry_id    UUID NOT NULL,

    account_id          UUID NOT NULL,

    direction           VARCHAR(6) NOT NULL,

    amount_minor        BIGINT NOT NULL,

    currency            CHAR(3) NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_posting_journal
        FOREIGN KEY (journal_entry_id)
            REFERENCES journal_entries(id),

    CONSTRAINT fk_posting_account_currency
        FOREIGN KEY (account_id, currency)
            REFERENCES ledger_accounts(id, currency),

    CONSTRAINT chk_posting_direction
        CHECK (
            direction IN ('DEBIT', 'CREDIT')
            ),

    CONSTRAINT chk_posting_amount
        CHECK (
            amount_minor > 0
            ),

    CONSTRAINT chk_posting_currency
        CHECK (
            currency ~ '^[A-Z]{3}$'
)
    );


CREATE INDEX idx_postings_journal_entry
    ON postings(journal_entry_id);


CREATE INDEX idx_postings_account_created
    ON postings(account_id, created_at);


CREATE INDEX idx_journal_entries_created
    ON journal_entries(created_at);