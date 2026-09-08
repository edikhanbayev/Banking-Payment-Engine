package kz.openbanking.ledger.ledger.api;

import java.util.UUID;

public record CreateJournalResponse(
        UUID journalEntryId
) {
}