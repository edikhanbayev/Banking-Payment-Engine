package kz.openbanking.ledger.idempotency.service;

import kz.openbanking.ledger.idempotency.domain.IdempotencyCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

@Component
public class IdempotencyCache {

    private static final Logger log =
            LoggerFactory.getLogger(
                    IdempotencyCache.class
            );


    private final StringRedisTemplate redisTemplate;

    private final JsonMapper jsonMapper;


    public IdempotencyCache(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper
    ) {

        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }


    public Optional<IdempotencyCacheEntry> get(
            String clientId,
            String idempotencyKey
    ) {

        try {

            String value =
                    redisTemplate.opsForValue().get(
                            buildKey(
                                    clientId,
                                    idempotencyKey
                            )
                    );


            if (value == null) {
                return Optional.empty();
            }


            return Optional.of(
                    jsonMapper.readValue(
                            value,
                            IdempotencyCacheEntry.class
                    )
            );

        }
        catch (Exception exception) {

            /*
             * Redis failure must NOT prevent
             * financial transactions.
             */

            log.warn(
                    "Redis idempotency cache unavailable",
                    exception
            );

            return Optional.empty();
        }
    }


    public void put(
            String clientId,
            String idempotencyKey,
            IdempotencyCacheEntry entry
    ) {

        try {

            String json =
                    jsonMapper.writeValueAsString(entry);


            redisTemplate.opsForValue().set(
                    buildKey(
                            clientId,
                            idempotencyKey
                    ),
                    json,
                    Duration.ofHours(24)
            );

        }
        catch (Exception exception) {

            log.warn(
                    "Could not cache idempotency result in Redis",
                    exception
            );
        }
    }


    private String buildKey(
            String clientId,
            String idempotencyKey
    ) {

        return "idempotency:"
                + clientId
                + ":"
                + idempotencyKey;
    }
}
