package kz.openbanking.ledger.payment.api;

import kz.openbanking.ledger.payment.domain.AccountNotFoundException;
import kz.openbanking.ledger.payment.domain.InsufficientFundsException;
import kz.openbanking.ledger.payment.domain.InvalidTransferException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse accountNotFound(
            AccountNotFoundException exception
    ) {

        return new ErrorResponse(
                "ACCOUNT_NOT_FOUND",
                exception.getMessage()
        );
    }


    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse insufficientFunds(
            InsufficientFundsException exception
    ) {

        return new ErrorResponse(
                "INSUFFICIENT_FUNDS",
                exception.getMessage()
        );
    }


    @ExceptionHandler(InvalidTransferException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidTransfer(
            InvalidTransferException exception
    ) {

        return new ErrorResponse(
                "INVALID_TRANSFER",
                exception.getMessage()
        );
    }


    public record ErrorResponse(
            String code,
            String message
    ) {
    }
}
