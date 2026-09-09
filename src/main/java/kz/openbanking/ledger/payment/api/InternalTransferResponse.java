package kz.openbanking.ledger.payment.api;

import java.util.UUID;

public record InternalTransferResponse(

        UUID paymentId,

        UUID journalEntryId,

        String status

) {
}
