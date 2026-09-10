package kz.openbanking.ledger.outbox.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {

    private final OutboxPublisherService publisher;


    public OutboxRelay(
            OutboxPublisherService publisher
    ) {

        this.publisher = publisher;
    }


    @Scheduled(fixedDelay = 1000)
    public void relay() {

        publisher.publishBatch();
    }
}
