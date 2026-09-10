package kz.openbanking.ledger.payment.domain;

public enum PaymentStatus {

    INITIATED,
    AUTHORIZED,
    PROCESSING,
    POSTED,
    FAILED,
    REVERSED
}
