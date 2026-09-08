package kz.openbanking.ledger.ledger.domain;

public class UnbalancedJournalException extends RuntimeException {

    public UnbalancedJournalException(String message) {
        super(message);
    }
}