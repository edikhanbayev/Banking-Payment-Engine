package kz.openbanking.ledger.payment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import kz.openbanking.ledger.payment.domain.PaymentStatus;

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
        jdbcTemplate.update("""
        INSERT INTO payment_status_history
        (
            payment_id,
            from_status,
            to_status,
            reason
        )
        VALUES (
            ?,
            NULL,
            'INITIATED',
            'Payment created'
        )
        """,
                paymentId
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
                      AND status = 'PROCESSING'
                    """,

                        journalEntryId,
                        paymentId
                );


        if (updated != 1) {

            throw new IllegalStateException(
                    "Payment could not transition " +
                            "PROCESSING -> POSTED"
            );
        }


        jdbcTemplate.update("""
            INSERT INTO payment_status_history
            (
                payment_id,
                from_status,
                to_status,
                reason
            )
            VALUES (
                ?,
                'PROCESSING',
                'POSTED',
                'Ledger journal committed'
            )
            """,

                paymentId
        );
    }

    public void transitionStatus(
            UUID paymentId,
            PaymentStatus expectedStatus,
            PaymentStatus newStatus,
            String reason
    ) {

        int updated =
                jdbcTemplate.update("""
                    UPDATE payments

                    SET status = ?

                    WHERE id = ?
                      AND status = ?
                    """,

                        newStatus.name(),
                        paymentId,
                        expectedStatus.name()
                );


        if (updated != 1) {

            throw new IllegalStateException(
                    "Invalid payment state transition: "
                            + expectedStatus
                            + " -> "
                            + newStatus
            );
        }


        jdbcTemplate.update("""
            INSERT INTO payment_status_history
            (
                payment_id,
                from_status,
                to_status,
                reason
            )
            VALUES (?, ?, ?, ?)
            """,

                paymentId,
                expectedStatus.name(),
                newStatus.name(),
                reason
        );
    }

}
