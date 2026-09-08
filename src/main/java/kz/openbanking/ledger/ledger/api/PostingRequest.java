package kz.openbanking.ledger.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import kz.openbanking.ledger.ledger.domain.PostingDirection;

import java.util.UUID;

public record PostingRequest(

        @NotNull
        UUID accountId,

        @NotNull
        PostingDirection direction,

        @Positive
        long amountMinor,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency

) {
}