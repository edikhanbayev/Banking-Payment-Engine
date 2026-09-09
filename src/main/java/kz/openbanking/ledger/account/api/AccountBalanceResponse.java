package kz.openbanking.ledger.account.api;

import java.util.UUID;

public record AccountBalanceResponse(

        UUID accountId,

        String accountType,

        String currency,

        String status,

        long postedBalanceMinor,

        long availableBalanceMinor,

        long version

) {
}
