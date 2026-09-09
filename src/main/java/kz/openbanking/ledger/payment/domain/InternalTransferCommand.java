package kz.openbanking.ledger.payment.domain;

import java.util.UUID;

public record InternalTransferCommand(

        UUID fromAccountId,

        UUID toAccountId,

        long amountMinor,

        String currency

) {
}
