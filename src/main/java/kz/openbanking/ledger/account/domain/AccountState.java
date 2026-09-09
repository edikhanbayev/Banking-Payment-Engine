package kz.openbanking.ledger.account.domain;

import java.util.UUID;

public record AccountState(

        UUID accountId,

        AccountType accountType,

        String currency,

        AccountStatus status,

        long postedBalanceMinor,

        long availableBalanceMinor,

        long version

) {
}
