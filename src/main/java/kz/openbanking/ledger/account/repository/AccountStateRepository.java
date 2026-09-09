package kz.openbanking.ledger.account.repository;

import kz.openbanking.ledger.account.domain.AccountState;
import kz.openbanking.ledger.account.domain.AccountStatus;
import kz.openbanking.ledger.account.domain.AccountType;
import kz.openbanking.ledger.ledger.domain.PostingCommand;
import kz.openbanking.ledger.ledger.domain.PostingDirection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountStateRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public Optional<AccountState> findById(UUID accountId) {

        List<AccountState> accounts =
                jdbcTemplate.query("""
                        SELECT
                            s.account_id,
                            a.account_type,
                            a.currency,
                            a.status,
                            s.posted_balance_minor,
                            s.available_balance_minor,
                            s.version
                        FROM account_state s
                        JOIN ledger_accounts a
                            ON a.id = s.account_id
                        WHERE s.account_id = ?
                        """,

                        (rs, rowNum) ->
                                new AccountState(
                                        rs.getObject(
                                                "account_id",
                                                UUID.class
                                        ),
                                        AccountType.valueOf(
                                                rs.getString(
                                                        "account_type"
                                                )
                                        ),
                                        rs.getString("currency"),
                                        AccountStatus.valueOf(
                                                rs.getString("status")
                                        ),
                                        rs.getLong(
                                                "posted_balance_minor"
                                        ),
                                        rs.getLong(
                                                "available_balance_minor"
                                        ),
                                        rs.getLong("version")
                                ),

                        accountId
                );

        return accounts.stream().findFirst();
    }


    public void applyPosting(
            PostingCommand posting
    ) {

        String accountTypeString =
                jdbcTemplate.queryForObject("""
                        SELECT account_type
                        FROM ledger_accounts
                        WHERE id = ?
                        """,
                        String.class,
                        posting.accountId()
                );

        AccountType accountType =
                AccountType.valueOf(accountTypeString);

        long delta =
                calculateBalanceDelta(
                        accountType,
                        posting.direction(),
                        posting.amountMinor()
                );

        int updatedRows =
                jdbcTemplate.update("""
                        UPDATE account_state

                        SET
                            posted_balance_minor =
                                posted_balance_minor + ?,

                            available_balance_minor =
                                available_balance_minor + ?,

                            version =
                                version + 1,

                            updated_at =
                                now()

                        WHERE account_id = ?
                        """,

                        delta,
                        delta,
                        posting.accountId()
                );

        if (updatedRows != 1) {

            throw new IllegalStateException(
                    "Account state does not exist for account "
                            + posting.accountId()
            );
        }
    }


    private long calculateBalanceDelta(
            AccountType accountType,
            PostingDirection direction,
            long amountMinor
    ) {

        boolean debitIncreases =
                accountType == AccountType.ASSET
                        || accountType == AccountType.EXPENSE;

        if (debitIncreases) {

            return direction == PostingDirection.DEBIT
                    ? amountMinor
                    : -amountMinor;
        }

        return direction == PostingDirection.CREDIT
                ? amountMinor
                : -amountMinor;
    }

    public Optional<AccountState> findByIdForUpdate(
            UUID accountId
    ) {

        List<AccountState> accounts =
                jdbcTemplate.query("""
                    SELECT
                        s.account_id,
                        a.account_type,
                        a.currency,
                        a.status,
                        s.posted_balance_minor,
                        s.available_balance_minor,
                        s.version

                    FROM account_state s

                    JOIN ledger_accounts a
                        ON a.id = s.account_id

                    WHERE s.account_id = ?

                    FOR UPDATE OF s
                    """,

                        (rs, rowNum) ->
                                new AccountState(
                                        rs.getObject(
                                                "account_id",
                                                UUID.class
                                        ),
                                        AccountType.valueOf(
                                                rs.getString(
                                                        "account_type"
                                                )
                                        ),
                                        rs.getString("currency"),
                                        AccountStatus.valueOf(
                                                rs.getString("status")
                                        ),
                                        rs.getLong(
                                                "posted_balance_minor"
                                        ),
                                        rs.getLong(
                                                "available_balance_minor"
                                        ),
                                        rs.getLong("version")
                                ),

                        accountId
                );

        return accounts.stream().findFirst();
    }
    public List<AccountState> findPairForUpdate(
            UUID accountId1,
            UUID accountId2
    ) {

        return jdbcTemplate.query("""
            SELECT
                s.account_id,
                a.account_type,
                a.currency,
                a.status,
                s.posted_balance_minor,
                s.available_balance_minor,
                s.version

            FROM account_state s

            JOIN ledger_accounts a
                ON a.id = s.account_id

            WHERE s.account_id IN (?, ?)

            ORDER BY s.account_id

            FOR UPDATE OF s
            """,

                (rs, rowNum) ->
                        new AccountState(
                                rs.getObject(
                                        "account_id",
                                        UUID.class
                                ),
                                AccountType.valueOf(
                                        rs.getString(
                                                "account_type"
                                        )
                                ),
                                rs.getString("currency"),
                                AccountStatus.valueOf(
                                        rs.getString("status")
                                ),
                                rs.getLong(
                                        "posted_balance_minor"
                                ),
                                rs.getLong(
                                        "available_balance_minor"
                                ),
                                rs.getLong("version")
                        ),

                accountId1,
                accountId2
        );
    }


}
