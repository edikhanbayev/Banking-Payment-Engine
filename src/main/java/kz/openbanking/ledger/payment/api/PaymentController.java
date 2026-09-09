package kz.openbanking.ledger.payment.api;

import jakarta.validation.Valid;
import kz.openbanking.ledger.payment.domain.InternalTransferCommand;
import kz.openbanking.ledger.payment.domain.InternalTransferResult;
import kz.openbanking.ledger.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
public class PaymentController {

    private final PaymentService paymentService;


    public PaymentController(
            PaymentService paymentService
    ) {

        this.paymentService =
                paymentService;
    }


    @PostMapping("/internal")
    @ResponseStatus(HttpStatus.CREATED)
    public InternalTransferResponse transfer(
            @Valid @RequestBody
            InternalTransferRequest request
    ) {

        InternalTransferCommand command =
                new InternalTransferCommand(
                        request.fromAccountId(),
                        request.toAccountId(),
                        request.amountMinor(),
                        request.currency()
                );


        InternalTransferResult result =
                paymentService.transfer(command);


        return new InternalTransferResponse(
                result.paymentId(),
                result.journalEntryId(),
                result.status().name()
        );
    }
}
