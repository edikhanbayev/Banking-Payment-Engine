CREATE TABLE account_state
(
    account_id              UUID PRIMARY KEY,

    posted_balance_minor    BIGINT NOT NULL DEFAULT 0,

    available_balance_minor BIGINT NOT NULL DEFAULT 0,

    version                 BIGINT NOT NULL DEFAULT 0,

    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_account_state_account
        FOREIGN KEY (account_id)
            REFERENCES ledger_accounts(id),

    CONSTRAINT chk_account_state_version
        CHECK (version >= 0)
);

WITH calculated_balances AS
         (
             SELECT
                 a.id AS account_id,

                 COALESCE(
                         SUM(
                                 CASE

                                     WHEN a.account_type IN ('ASSET', 'EXPENSE')
                                         AND p.direction = 'DEBIT'
                                         THEN p.amount_minor

                                     WHEN a.account_type IN ('ASSET', 'EXPENSE')
                                         AND p.direction = 'CREDIT'
                                         THEN -p.amount_minor

                                     WHEN a.account_type IN (
                                                             'LIABILITY',
                                                             'EQUITY',
                                                             'REVENUE'
                                         )
                                         AND p.direction = 'CREDIT'
                                         THEN p.amount_minor

                                     WHEN a.account_type IN (
                                                             'LIABILITY',
                                                             'EQUITY',
                                                             'REVENUE'
                                         )
                                         AND p.direction = 'DEBIT'
                                         THEN -p.amount_minor

                                     ELSE 0

                                     END
                         ),
                         0
                 ) AS balance_minor,

                 COUNT(p.id) AS posting_count

             FROM ledger_accounts a

                      LEFT JOIN postings p
                                ON p.account_id = a.id

             GROUP BY a.id
         )

INSERT INTO account_state
(
    account_id,
    posted_balance_minor,
    available_balance_minor,
    version
)

SELECT
    account_id,
    balance_minor,
    balance_minor,
    posting_count
FROM calculated_balances;

CREATE OR REPLACE FUNCTION create_account_state_for_new_account()
RETURNS TRIGGER
LANGUAGE plpgsql
AS
$$
BEGIN

INSERT INTO account_state(account_id)
VALUES (NEW.id);

RETURN NEW;

END;
$$;


CREATE TRIGGER initialize_account_state
    AFTER INSERT
    ON ledger_accounts
    FOR EACH ROW
    EXECUTE FUNCTION create_account_state_for_new_account();
