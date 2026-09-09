package kz.openbanking.ledger.payment.domain;

import java.util.UUID;

public record InternalTransferResult(

        UUID paymentId,

        UUID journalEntryId,

        PaymentStatus status

) {
}
