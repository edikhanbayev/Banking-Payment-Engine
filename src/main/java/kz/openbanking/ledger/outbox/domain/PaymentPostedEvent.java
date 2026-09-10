package kz.openbanking.ledger.outbox.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentPostedEvent(

        UUID eventId,

        UUID paymentId,

        UUID journalEntryId,

        UUID debtorAccountId,

        UUID creditorAccountId,

        long amountMinor,

        String currency,

        Instant occurredAt

) {
}
