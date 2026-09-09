CREATE TABLE payments
(
    id                  UUID PRIMARY KEY,

    payment_type        VARCHAR(30) NOT NULL,

    debtor_account_id   UUID NOT NULL,

    creditor_account_id UUID NOT NULL,

    amount_minor        BIGINT NOT NULL,

    currency            CHAR(3) NOT NULL,

    status              VARCHAR(20) NOT NULL,

    journal_entry_id    UUID,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    posted_at           TIMESTAMPTZ,

    CONSTRAINT fk_payment_debtor
        FOREIGN KEY (debtor_account_id)
            REFERENCES ledger_accounts(id),

    CONSTRAINT fk_payment_creditor
        FOREIGN KEY (creditor_account_id)
            REFERENCES ledger_accounts(id),

    CONSTRAINT fk_payment_journal
        FOREIGN KEY (journal_entry_id)
            REFERENCES journal_entries(id),

    CONSTRAINT uq_payment_journal
        UNIQUE (journal_entry_id),

    CONSTRAINT chk_payment_type
        CHECK (
            payment_type IN ('INTERNAL')
            ),

    CONSTRAINT chk_payment_status
        CHECK (
            status IN (
                       'INITIATED',
                       'POSTED',
                       'FAILED',
                       'REVERSED'
                )
            ),

    CONSTRAINT chk_payment_amount
        CHECK (amount_minor > 0),

    CONSTRAINT chk_payment_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_payment_accounts_different
        CHECK (
            debtor_account_id <> creditor_account_id
        )
);


CREATE INDEX idx_payments_debtor
    ON payments(debtor_account_id, created_at);


CREATE INDEX idx_payments_creditor
    ON payments(creditor_account_id, created_at);


CREATE INDEX idx_payments_status
    ON payments(status);
