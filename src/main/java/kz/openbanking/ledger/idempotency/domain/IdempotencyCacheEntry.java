package kz.openbanking.ledger.idempotency.domain;

public record IdempotencyCacheEntry(

        String requestHash,

        String responseBody

) {
}
