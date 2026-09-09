package kz.openbanking.ledger.payment.domain;

public class InsufficientFundsException
        extends RuntimeException {

    public InsufficientFundsException() {

        super(
                "Insufficient available funds"
        );
    }
}
