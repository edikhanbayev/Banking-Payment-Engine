package kz.openbanking.ledger.payment.service;

import kz.openbanking.ledger.account.domain.AccountState;
import kz.openbanking.ledger.account.domain.AccountStatus;
import kz.openbanking.ledger.account.repository.AccountStateRepository;
import kz.openbanking.ledger.ledger.domain.JournalCommand;
import kz.openbanking.ledger.ledger.domain.PostingCommand;
import kz.openbanking.ledger.ledger.domain.PostingDirection;
import kz.openbanking.ledger.ledger.service.LedgerService;
import kz.openbanking.ledger.payment.domain.*;
import kz.openbanking.ledger.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final AccountStateRepository accountStateRepository;

    private final PaymentRepository paymentRepository;

    private final LedgerService ledgerService;


    public PaymentService(
            AccountStateRepository accountStateRepository,
            PaymentRepository paymentRepository,
            LedgerService ledgerService
    ) {

        this.accountStateRepository =
                accountStateRepository;

        this.paymentRepository =
                paymentRepository;

        this.ledgerService =
                ledgerService;
    }


    @Transactional
    public InternalTransferResult transfer(
            InternalTransferCommand command
    ) {

        validateBasicRequest(command);


        /*
         * IMPORTANT:
         *
         * Both account rows are acquired in one
         * deterministic PostgreSQL lock order.
         *
         * This protects against:
         *
         * A -> B
         * B -> A
         *
         * locking rows in opposite order.
         */

        List<AccountState> lockedAccounts =
                accountStateRepository
                        .findPairForUpdate(
                                command.fromAccountId(),
                                command.toAccountId()
                        );


        Map<UUID, AccountState> accountsById =
                lockedAccounts
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        AccountState::accountId,
                                        Function.identity()
                                )
                        );


        AccountState debtor =
                accountsById.get(
                        command.fromAccountId()
                );


        if (debtor == null) {

            throw new AccountNotFoundException(
                    command.fromAccountId()
            );
        }


        AccountState creditor =
                accountsById.get(
                        command.toAccountId()
                );


        if (creditor == null) {

            throw new AccountNotFoundException(
                    command.toAccountId()
            );
        }


        validateAccounts(
                debtor,
                creditor,
                command
        );


        /*
         * This check is now safe because the
         * debtor's account_state row is locked.
         */

        if (
                debtor.availableBalanceMinor()
                        < command.amountMinor()
        ) {

            throw new InsufficientFundsException();
        }


        UUID paymentId =
                UUID.randomUUID();


        paymentRepository.insertInitiated(
                paymentId,
                debtor.accountId(),
                creditor.accountId(),
                command.amountMinor(),
                command.currency()
        );


        JournalCommand journal =
                new JournalCommand(
                        "PAYMENT",
                        paymentId,
                        "Internal transfer "
                                + paymentId,

                        List.of(

                                new PostingCommand(
                                        debtor.accountId(),
                                        PostingDirection.DEBIT,
                                        command.amountMinor(),
                                        command.currency()
                                ),

                                new PostingCommand(
                                        creditor.accountId(),
                                        PostingDirection.CREDIT,
                                        command.amountMinor(),
                                        command.currency()
                                )
                        )
                );


        UUID journalId =
                ledgerService.postJournal(
                        journal
                );


        paymentRepository.markPosted(
                paymentId,
                journalId
        );


        return new InternalTransferResult(
                paymentId,
                journalId,
                PaymentStatus.POSTED
        );
    }


    private void validateBasicRequest(
            InternalTransferCommand command
    ) {

        if (
                command.fromAccountId()
                        .equals(
                                command.toAccountId()
                        )
        ) {

            throw new InvalidTransferException(
                    "Source and destination accounts must be different"
            );
        }


        if (
                command.amountMinor() <= 0
        ) {

            throw new InvalidTransferException(
                    "Transfer amount must be positive"
            );
        }
    }


    private void validateAccounts(
            AccountState debtor,
            AccountState creditor,
            InternalTransferCommand command
    ) {

        if (
                debtor.status()
                        != AccountStatus.OPEN
        ) {

            throw new InvalidTransferException(
                    "Debtor account is not OPEN"
            );
        }


        if (
                creditor.status()
                        != AccountStatus.OPEN
        ) {

            throw new InvalidTransferException(
                    "Creditor account is not OPEN"
            );
        }


        if (
                !debtor.currency()
                        .equals(
                                command.currency()
                        )
        ) {

            throw new InvalidTransferException(
                    "Debtor account currency does not match transfer currency"
            );
        }


        if (
                !creditor.currency()
                        .equals(
                                command.currency()
                        )
        ) {

            throw new InvalidTransferException(
                    "Creditor account currency does not match transfer currency"
            );
        }
    }
}
