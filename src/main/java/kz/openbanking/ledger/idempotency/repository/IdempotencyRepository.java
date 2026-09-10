package kz.openbanking.ledger.idempotency.repository;

import kz.openbanking.ledger.idempotency.domain.IdempotencyRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;


    public IdempotencyRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public boolean tryStart(
            String clientId,
            String idempotencyKey,
            String requestHash
    ) {

        int inserted =
                jdbcTemplate.update("""
                        INSERT INTO api_idempotency
                        (
                            client_id,
                            idempotency_key,
                            request_hash,
                            status
                        )
                        VALUES (?, ?, ?, 'PROCESSING')

                        ON CONFLICT
                        (
                            client_id,
                            idempotency_key
                        )
                        DO NOTHING
                        """,
                        clientId,
                        idempotencyKey,
                        requestHash
                );

        return inserted == 1;
    }


    public IdempotencyRecord findForUpdate(
            String clientId,
            String idempotencyKey
    ) {

        return jdbcTemplate.queryForObject("""
                        SELECT
                            client_id,
                            idempotency_key,
                            request_hash,
                            status,
                            payment_id,
                            journal_entry_id,
                            http_status,
                            response_body::text
                        FROM api_idempotency
                        WHERE client_id = ?
                          AND idempotency_key = ?
                        FOR UPDATE
                        """,

                (rs, rowNum) ->
                        new IdempotencyRecord(
                                rs.getString("client_id"),
                                rs.getString("idempotency_key"),
                                rs.getString("request_hash"),
                                rs.getString("status"),
                                rs.getObject(
                                        "payment_id",
                                        java.util.UUID.class
                                ),
                                rs.getObject(
                                        "journal_entry_id",
                                        java.util.UUID.class
                                ),
                                rs.getObject(
                                        "http_status",
                                        Integer.class
                                ),
                                rs.getString("response_body")
                        ),

                clientId,
                idempotencyKey
        );
    }


    public void complete(
            String clientId,
            String idempotencyKey,
            java.util.UUID paymentId,
            java.util.UUID journalEntryId,
            String responseBody
    ) {

        int updated =
                jdbcTemplate.update("""
                        UPDATE api_idempotency

                        SET
                            status = 'COMPLETED',
                            payment_id = ?,
                            journal_entry_id = ?,
                            http_status = 201,
                            response_body = CAST(? AS jsonb),
                            completed_at = now()

                        WHERE client_id = ?
                          AND idempotency_key = ?
                          AND status = 'PROCESSING'
                        """,

                        paymentId,
                        journalEntryId,
                        responseBody,
                        clientId,
                        idempotencyKey
                );


        if (updated != 1) {
            throw new IllegalStateException(
                    "Idempotency record could not be completed"
            );
        }
    }
}
