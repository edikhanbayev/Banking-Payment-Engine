package kz.openbanking.ledger.ledger.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateJournalRequest(

        @NotBlank
        String referenceType,

        @NotNull
        UUID referenceId,

        String description,

        @NotEmpty
        List<@Valid PostingRequest> postings

) {
}