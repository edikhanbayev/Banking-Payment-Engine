package kz.openbanking.ledger.outbox.service;

import kz.openbanking.ledger.outbox.domain.OutboxEvent;
import kz.openbanking.ledger.outbox.domain.PaymentPostedEvent;
import kz.openbanking.ledger.outbox.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;

    private final JsonMapper jsonMapper;


    public OutboxService(
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper
    ) {

        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }


    public void paymentPosted(
            UUID paymentId,
            UUID journalEntryId,
            UUID debtorAccountId,
            UUID creditorAccountId,
            long amountMinor,
            String currency
    ) {

        UUID eventId =
                UUID.randomUUID();


        PaymentPostedEvent event =
                new PaymentPostedEvent(
                        eventId,
                        paymentId,
                        journalEntryId,
                        debtorAccountId,
                        creditorAccountId,
                        amountMinor,
                        currency,
                        Instant.now()
                );


        String payload;

        try {

            payload =
                    jsonMapper.writeValueAsString(event);

        }
        catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not serialize outbox event",
                    exception
            );
        }


        outboxRepository.insert(
                new OutboxEvent(
                        eventId,
                        "PAYMENT",
                        paymentId,
                        "PaymentPosted",
                        "payment.events",
                        paymentId.toString(),
                        payload
                )
        );
    }
}
