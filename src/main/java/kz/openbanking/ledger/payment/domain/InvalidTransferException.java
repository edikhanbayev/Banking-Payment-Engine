package kz.openbanking.ledger.payment.domain;

public class InvalidTransferException
        extends RuntimeException {

    public InvalidTransferException(
            String message
    ) {

        super(message);
    }
}
