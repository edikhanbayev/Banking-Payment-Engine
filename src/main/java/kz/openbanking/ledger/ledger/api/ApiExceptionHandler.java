package kz.openbanking.ledger.ledger.api;

import kz.openbanking.ledger.ledger.domain.UnbalancedJournalException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnbalancedJournalException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnbalancedJournal(
            UnbalancedJournalException exception
    ) {

        return new ErrorResponse(
                "UNBALANCED_JOURNAL",
                exception.getMessage()
        );
    }

    public record ErrorResponse(
            String code,
            String message
    ) {
    }
}