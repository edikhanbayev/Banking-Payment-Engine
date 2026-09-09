package kz.openbanking.ledger.ledger.service;

import kz.openbanking.ledger.account.repository.AccountStateRepository;
import kz.openbanking.ledger.ledger.domain.JournalCommand;
import kz.openbanking.ledger.ledger.domain.JournalValidator;
import kz.openbanking.ledger.ledger.domain.PostingCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LedgerService {

    private final JdbcTemplate jdbcTemplate;
    private final JournalValidator journalValidator;
    private final AccountStateRepository accountStateRepository;

    public LedgerService(
            JdbcTemplate jdbcTemplate,
            JournalValidator journalValidator,
            AccountStateRepository accountStateRepository
    ) {

        this.jdbcTemplate = jdbcTemplate;
        this.journalValidator = journalValidator;
        this.accountStateRepository = accountStateRepository;
    }


    @Transactional
    public UUID postJournal(
            JournalCommand command
    ) {

        journalValidator.validate(command);

        UUID journalId =
                UUID.randomUUID();


        jdbcTemplate.update("""
                INSERT INTO journal_entries
                (
                    id,
                    reference_type,
                    reference_id,
                    description
                )
                VALUES (?, ?, ?, ?)
                """,

                journalId,
                command.referenceType(),
                command.referenceId(),
                command.description()
        );


        for (PostingCommand posting :
                command.postings()) {

            UUID postingId =
                    UUID.randomUUID();


            jdbcTemplate.update("""
                    INSERT INTO postings
                    (
                        id,
                        journal_entry_id,
                        account_id,
                        direction,
                        amount_minor,
                        currency
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,

                    postingId,
                    journalId,
                    posting.accountId(),
                    posting.direction().name(),
                    posting.amountMinor(),
                    posting.currency()
            );


            accountStateRepository.applyPosting(
                    posting
            );
        }


        return journalId;
    }
}
