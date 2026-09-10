package kz.openbanking.ledger.idempotency.domain;

public class IdempotencyInProgressException
        extends RuntimeException {

    public IdempotencyInProgressException() {
        super(
                "A request with this Idempotency-Key " +
                        "is already being processed"
        );
    }
}
