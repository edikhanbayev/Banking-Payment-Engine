package kz.openbanking.ledger.ledger.api;

import jakarta.validation.Valid;
import kz.openbanking.ledger.ledger.domain.JournalCommand;
import kz.openbanking.ledger.ledger.domain.PostingCommand;
import kz.openbanking.ledger.ledger.service.LedgerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/journals")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateJournalResponse createJournal(
            @Valid @RequestBody CreateJournalRequest request
    ) {

        List<PostingCommand> postings =
                request.postings()
                        .stream()
                        .map(posting ->
                                new PostingCommand(
                                        posting.accountId(),
                                        posting.direction(),
                                        posting.amountMinor(),
                                        posting.currency()
                                )
                        )
                        .toList();

        JournalCommand command =
                new JournalCommand(
                        request.referenceType(),
                        request.referenceId(),
                        request.description(),
                        postings
                );

        UUID journalId =
                ledgerService.postJournal(command);

        return new CreateJournalResponse(journalId);
    }
}