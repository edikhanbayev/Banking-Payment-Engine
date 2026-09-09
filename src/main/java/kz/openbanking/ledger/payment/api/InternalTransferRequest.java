package kz.openbanking.ledger.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record InternalTransferRequest(

        @NotNull
        UUID fromAccountId,

        @NotNull
        UUID toAccountId,

        @Positive
        long amountMinor,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency

) {
}
