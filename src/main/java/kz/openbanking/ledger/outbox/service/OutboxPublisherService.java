package kz.openbanking.ledger.outbox.service;

import kz.openbanking.ledger.outbox.domain.OutboxEvent;
import kz.openbanking.ledger.outbox.repository.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisherService {

    private final OutboxRepository outboxRepository;

    private final KafkaTemplate<String, String>
            kafkaTemplate;


    public OutboxPublisherService(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {

        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }


    @Transactional
    public void publishBatch() {

        List<OutboxEvent> events =
                outboxRepository
                        .findPendingForUpdate(50);


        for (OutboxEvent event : events) {

            try {

                kafkaTemplate
                        .send(
                                event.topic(),
                                event.eventKey(),
                                event.payload()
                        )
                        .get(
                                10,
                                TimeUnit.SECONDS
                        );


                outboxRepository.markPublished(
                        event.id()
                );

            }
            catch (Exception exception) {

                String message =
                        exception.getMessage();

                if (
                        message != null
                                && message.length() > 900
                ) {

                    message =
                            message.substring(0, 900);
                }


                outboxRepository.markFailed(
                        event.id(),
                        message
                );
            }
        }
    }
}
