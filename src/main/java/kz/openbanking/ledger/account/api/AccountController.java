package kz.openbanking.ledger.account.api;

import kz.openbanking.ledger.account.domain.AccountState;
import kz.openbanking.ledger.account.repository.AccountStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/accounts")
public class AccountController {

    private final AccountStateRepository accountStateRepository;

    public AccountController(
            AccountStateRepository accountStateRepository
    ) {
        this.accountStateRepository =
                accountStateRepository;
    }


    @GetMapping("/{accountId}/balance")
    public AccountBalanceResponse getBalance(
            @PathVariable UUID accountId
    ) {

        AccountState account =
                accountStateRepository
                        .findById(accountId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Account not found"
                                        )
                        );


        return new AccountBalanceResponse(
                account.accountId(),
                account.accountType().name(),
                account.currency(),
                account.status().name(),
                account.postedBalanceMinor(),
                account.availableBalanceMinor(),
                account.version()
        );
    }
}
