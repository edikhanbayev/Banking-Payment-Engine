package kz.openbanking.ledger.outbox.repository;

import kz.openbanking.ledger.outbox.domain.OutboxEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;


    public OutboxRepository(
            JdbcTemplate jdbcTemplate
    ) {

        this.jdbcTemplate = jdbcTemplate;
    }


    public void insert(
            OutboxEvent event
    ) {

        jdbcTemplate.update("""
                INSERT INTO outbox_events
                (
                    id,
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    topic,
                    event_key,
                    payload
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, CAST(? AS jsonb)
                )
                """,

                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.topic(),
                event.eventKey(),
                event.payload()
        );
    }
    public List<OutboxEvent> findPendingForUpdate(
            int limit
    ) {

        return jdbcTemplate.query("""
            SELECT
                id,
                aggregate_type,
                aggregate_id,
                event_type,
                topic,
                event_key,
                payload::text

            FROM outbox_events

            WHERE published_at IS NULL

            ORDER BY created_at, id

            LIMIT ?

            FOR UPDATE SKIP LOCKED
            """,

                (rs, rowNum) ->
                        new OutboxEvent(
                                rs.getObject(
                                        "id",
                                        UUID.class
                                ),
                                rs.getString(
                                        "aggregate_type"
                                ),
                                rs.getObject(
                                        "aggregate_id",
                                        UUID.class
                                ),
                                rs.getString(
                                        "event_type"
                                ),
                                rs.getString("topic"),
                                rs.getString("event_key"),
                                rs.getString("payload")
                        ),

                limit
        );
    }
    public void markPublished(
            UUID eventId
    ) {

        jdbcTemplate.update("""
            UPDATE outbox_events

            SET
                published_at = now(),
                attempt_count = attempt_count + 1,
                last_error = NULL

            WHERE id = ?
            """,

                eventId
        );
    }
    public void markFailed(
            UUID eventId,
            String error
    ) {

        jdbcTemplate.update("""
            UPDATE outbox_events

            SET
                attempt_count = attempt_count + 1,
                last_error = ?

            WHERE id = ?
            """,

                error,
                eventId
        );
    }

}
