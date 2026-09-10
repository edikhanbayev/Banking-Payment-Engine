package kz.openbanking.ledger.idempotency.service;

import kz.openbanking.ledger.idempotency.domain.*;
import kz.openbanking.ledger.idempotency.repository.IdempotencyRepository;
import kz.openbanking.ledger.payment.domain.InternalTransferResult;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final JsonMapper jsonMapper;
    private final IdempotencyCache cache;


    public IdempotencyService(
            IdempotencyRepository repository,
            JsonMapper jsonMapper,
            IdempotencyCache cache
    ) {

        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.cache = cache;
    }


    public IdempotencyDecision acquire(
            String clientId,
            String idempotencyKey,
            String requestHash
    ) {

        validateHeaders(
                clientId,
                idempotencyKey
        );


        Optional<IdempotencyCacheEntry> cached =
                cache.get(
                        clientId,
                        idempotencyKey
                );


        if (cached.isPresent()) {

            IdempotencyCacheEntry entry =
                    cached.get();


            if (!entry.requestHash().equals(requestHash)) {

                throw new IdempotencyConflictException();
            }


            return IdempotencyDecision.replay(
                    deserialize(
                            entry.responseBody()
                    )
            );
        }


        boolean started =
                repository.tryStart(
                        clientId,
                        idempotencyKey,
                        requestHash
                );


        if (started) {
            return IdempotencyDecision.newRequest();
        }


        IdempotencyRecord existing =
                repository.findForUpdate(
                        clientId,
                        idempotencyKey
                );


        if (!existing.requestHash().equals(requestHash)) {

            throw new IdempotencyConflictException();
        }


        if ("COMPLETED".equals(existing.status())) {

            InternalTransferResult result =
                    deserialize(
                            existing.responseBody()
                    );


            cacheAfterCommit(
                    clientId,
                    idempotencyKey,
                    requestHash,
                    existing.responseBody()
            );


            return IdempotencyDecision.replay(
                    result
            );
        }


        throw new IdempotencyInProgressException();
    }


    public void complete(
            String clientId,
            String idempotencyKey,
            String requestHash,
            InternalTransferResult result
    ) {

        String response =
                serialize(result);


        repository.complete(
                clientId,
                idempotencyKey,
                result.paymentId(),
                result.journalEntryId(),
                response
        );


        cacheAfterCommit(
                clientId,
                idempotencyKey,
                requestHash,
                response
        );
    }


    private void cacheAfterCommit(
            String clientId,
            String idempotencyKey,
            String requestHash,
            String responseBody
    ) {

        Runnable cacheWrite =
                () -> cache.put(
                        clientId,
                        idempotencyKey,
                        new IdempotencyCacheEntry(
                                requestHash,
                                responseBody
                        )
                );


        if (
                TransactionSynchronizationManager
                        .isSynchronizationActive()
        ) {

            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {

                                @Override
                                public void afterCommit() {
                                    cacheWrite.run();
                                }
                            }
                    );
        }
        else {
            cacheWrite.run();
        }
    }


    private String serialize(
            InternalTransferResult result
    ) {

        try {
            return jsonMapper.writeValueAsString(result);
        }
        catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not serialize idempotent response",
                    exception
            );
        }
    }


    private InternalTransferResult deserialize(
            String json
    ) {

        try {

            return jsonMapper.readValue(
                    json,
                    InternalTransferResult.class
            );

        }
        catch (Exception exception) {

            throw new IllegalStateException(
                    "Could not deserialize idempotent response",
                    exception
            );
        }
    }


    private void validateHeaders(
            String clientId,
            String idempotencyKey
    ) {

        if (clientId == null || clientId.isBlank()) {

            throw new IllegalArgumentException(
                    "X-Client-Id is required"
            );
        }


        if (
                idempotencyKey == null
                        || idempotencyKey.isBlank()
                        || idempotencyKey.length() > 128
        ) {

            throw new IllegalArgumentException(
                    "Valid Idempotency-Key is required"
            );
        }
    }
}