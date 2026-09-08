package kz.openbanking.ledger.ledger.domain;

import java.util.UUID;

public record PostingCommand(
        UUID accountId,
        PostingDirection direction,
        long amountMinor,
        String currency
) {
}