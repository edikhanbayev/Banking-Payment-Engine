CREATE TABLE api_idempotency
(
    client_id           VARCHAR(100) NOT NULL,

    idempotency_key     VARCHAR(128) NOT NULL,

    request_hash        CHAR(64) NOT NULL,

    status              VARCHAR(20) NOT NULL,

    payment_id          UUID,

    journal_entry_id    UUID,

    http_status         INTEGER,

    response_body       JSONB,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    completed_at        TIMESTAMPTZ,

    PRIMARY KEY (
                 client_id,
                 idempotency_key
        ),

    CONSTRAINT chk_idempotency_status
        CHECK (
            status IN (
                       'PROCESSING',
                       'COMPLETED'
                )
            ),

    CONSTRAINT fk_idempotency_payment
        FOREIGN KEY (payment_id)
            REFERENCES payments(id),

    CONSTRAINT fk_idempotency_journal
        FOREIGN KEY (journal_entry_id)
            REFERENCES journal_entries(id)
);


CREATE INDEX idx_api_idempotency_created
    ON api_idempotency(created_at);
