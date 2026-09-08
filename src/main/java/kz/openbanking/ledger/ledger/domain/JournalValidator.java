package kz.openbanking.ledger.ledger.domain;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JournalValidator {

    public void validate(JournalCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "Journal command cannot be null"
            );
        }

        List<PostingCommand> postings = command.postings();

        if (postings == null || postings.size() < 2) {
            throw new UnbalancedJournalException(
                    "A journal entry must contain at least two postings"
            );
        }

        Map<String, Long> balancesByCurrency =
                new HashMap<>();

        for (PostingCommand posting : postings) {

            if (posting.amountMinor() <= 0) {
                throw new IllegalArgumentException(
                        "Posting amount must be greater than zero"
                );
            }

            if (posting.currency() == null ||
                    !posting.currency().matches("^[A-Z]{3}$")) {

                throw new IllegalArgumentException(
                        "Currency must contain three uppercase letters"
                );
            }

            long signedAmount =
                    switch (posting.direction()) {

                        case DEBIT ->
                                posting.amountMinor();

                        case CREDIT ->
                                -posting.amountMinor();
                    };

            balancesByCurrency.merge(
                    posting.currency(),
                    signedAmount,
                    Long::sum
            );
        }

        for (Map.Entry<String, Long> balance :
                balancesByCurrency.entrySet()) {

            if (balance.getValue() != 0) {

                throw new UnbalancedJournalException(
                        "Journal does not balance for currency "
                                + balance.getKey()
                                + ". Difference: "
                                + balance.getValue()
                );
            }
        }
    }
}