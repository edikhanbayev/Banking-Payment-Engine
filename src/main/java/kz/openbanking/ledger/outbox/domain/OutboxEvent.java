package kz.openbanking.ledger.outbox.domain;

import java.util.UUID;

public record OutboxEvent(

        UUID id,

        String aggregateType,

        UUID aggregateId,

        String eventType,

        String topic,

        String eventKey,

        String payload

) {
}
