ALTER TABLE payments
DROP CONSTRAINT chk_payment_status;


ALTER TABLE payments
    ADD CONSTRAINT chk_payment_status
        CHECK (
            status IN (
                       'INITIATED',
                       'AUTHORIZED',
                       'PROCESSING',
                       'POSTED',
                       'FAILED',
                       'REVERSED'
                )
            );


CREATE TABLE payment_status_history
(
    id              BIGSERIAL PRIMARY KEY,

    payment_id      UUID NOT NULL,

    from_status     VARCHAR(20),

    to_status       VARCHAR(20) NOT NULL,

    reason          VARCHAR(500),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_payment_status_history_payment
        FOREIGN KEY (payment_id)
            REFERENCES payments(id),

    CONSTRAINT chk_payment_history_to_status
        CHECK (
            to_status IN (
                          'INITIATED',
                          'AUTHORIZED',
                          'PROCESSING',
                          'POSTED',
                          'FAILED',
                          'REVERSED'
                )
            )
);


CREATE INDEX idx_payment_status_history_payment
    ON payment_status_history(
                              payment_id,
                              created_at
        );


INSERT INTO payment_status_history
(
    payment_id,
    from_status,
    to_status,
    reason
)
SELECT
    id,
    NULL,
    status,
    'Backfilled from existing payment'
FROM payments;
