package kz.openbanking.ledger.idempotency.domain;

import java.util.UUID;

public record IdempotencyRecord(

        String clientId,

        String idempotencyKey,

        String requestHash,

        String status,

        UUID paymentId,

        UUID journalEntryId,

        Integer httpStatus,

        String responseBody

) {
}
