package kz.openbanking.ledger.ledger.domain;

import java.util.List;
import java.util.UUID;

public record JournalCommand(
        String referenceType,
        UUID referenceId,
        String description,
        List<PostingCommand> postings
) {
}