package kz.openbanking.ledger.idempotency.domain;

import kz.openbanking.ledger.payment.domain.InternalTransferResult;

public record IdempotencyDecision(

        boolean replay,

        InternalTransferResult result

) {

    public static IdempotencyDecision newRequest() {

        return new IdempotencyDecision(
                false,
                null
        );
    }


    public static IdempotencyDecision replay(
            InternalTransferResult result
    ) {

        return new IdempotencyDecision(
                true,
                result
        );
    }
}
