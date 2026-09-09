package kz.openbanking.ledger.payment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;


    public PaymentRepository(
            JdbcTemplate jdbcTemplate
    ) {

        this.jdbcTemplate =
                jdbcTemplate;
    }


    public void insertInitiated(
            UUID paymentId,
            UUID debtorAccountId,
            UUID creditorAccountId,
            long amountMinor,
            String currency
    ) {

        jdbcTemplate.update("""
                INSERT INTO payments
                (
                    id,
                    payment_type,
                    debtor_account_id,
                    creditor_account_id,
                    amount_minor,
                    currency,
                    status
                )
                VALUES
                (
                    ?,
                    'INTERNAL',
                    ?,
                    ?,
                    ?,
                    ?,
                    'INITIATED'
                )
                """,

                paymentId,
                debtorAccountId,
                creditorAccountId,
                amountMinor,
                currency
        );
    }


    public void markPosted(
            UUID paymentId,
            UUID journalEntryId
    ) {

        int updated =
                jdbcTemplate.update("""
                        UPDATE payments

                        SET
                            status = 'POSTED',
                            journal_entry_id = ?,
                            posted_at = now()

                        WHERE id = ?
                          AND status = 'INITIATED'
                        """,

                        journalEntryId,
                        paymentId
                );


        if (updated != 1) {

            throw new IllegalStateException(
                    "Payment could not be marked POSTED: "
                            + paymentId
            );
        }
    }
}
