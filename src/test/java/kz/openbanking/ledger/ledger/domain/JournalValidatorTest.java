package kz.openbanking.ledger.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalValidatorTest {

    private final JournalValidator validator =
            new JournalValidator();

    @Test
    void balancedJournalShouldPass() {

        JournalCommand command =
                new JournalCommand(
                        "TEST",
                        UUID.randomUUID(),
                        "Valid journal",
                        List.of(
                                new PostingCommand(
                                        UUID.randomUUID(),
                                        PostingDirection.DEBIT,
                                        1000,
                                        "KZT"
                                ),
                                new PostingCommand(
                                        UUID.randomUUID(),
                                        PostingDirection.CREDIT,
                                        1000,
                                        "KZT"
                                )
                        )
                );

        assertDoesNotThrow(
                () -> validator.validate(command)
        );
    }


    @Test
    void unbalancedJournalShouldFail() {

        JournalCommand command =
                new JournalCommand(
                        "TEST",
                        UUID.randomUUID(),
                        "Invalid journal",
                        List.of(
                                new PostingCommand(
                                        UUID.randomUUID(),
                                        PostingDirection.DEBIT,
                                        1000,
                                        "KZT"
                                ),
                                new PostingCommand(
                                        UUID.randomUUID(),
                                        PostingDirection.CREDIT,
                                        900,
                                        "KZT"
                                )
                        )
                );

        assertThrows(
                UnbalancedJournalException.class,
                () -> validator.validate(command)
        );
    }


    @Test
    void journalMustBalancePerCurrency() {

        JournalCommand command =
                new JournalCommand(
                        "TEST",
                        UUID.randomUUID(),
                        "Cross currency error",
                        List.of(
                                new PostingCommand(
                                        UUID.randomUUID(),
                                        PostingDirection.DEBIT,
                                        1000,
                                        "USD"
                                ),
                                new PostingCommand(
                                        UUID.randomUUID(),
                                        PostingDirection.CREDIT,
                                        1000,
                                        "KZT"
                                )
                        )
                );

        assertThrows(
                UnbalancedJournalException.class,
                () -> validator.validate(command)
        );
    }
}