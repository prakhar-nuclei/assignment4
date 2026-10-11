package com.nuclei.orderservice.cache;

import com.nuclei.orderservice.dtos.OrderResponseDto;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

@Component
public class OrderIdempotencyCache {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OrderIdempotencyCache.class);

    private static final String CACHE_KEY_PREFIX = "order:idempotency:";

    private final RedisTemplate<String, OrderResponseDto> redisTemplate;
    private final Duration ttl;

    public OrderIdempotencyCache(
            @Qualifier("orderIdempotencyRedisTemplate")
            final RedisTemplate<String, OrderResponseDto> redisTemplate,
            @Value("${order.idempotency-cache.ttl:5m}")
            final Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public Optional<OrderResponseDto> get(
            final Long userId,
            final String idempotencyKey) {

        Optional<OrderResponseDto> result;

        try {
            final String cacheKey = buildCacheKey(userId, idempotencyKey);

            final OrderResponseDto cachedResponse =
                    redisTemplate.opsForValue().get(cacheKey);

            result = Optional.ofNullable(cachedResponse);
        } catch (DataAccessException | SerializationException exception) {
            LOGGER.warn(
                    "Idempotency cache read failed for userId={}; falling back to database",
                    userId,
                    exception);

            result = Optional.empty();
        }

        return result;
    }

    public void put(
            final Long userId,
            final String idempotencyKey,
            final OrderResponseDto response) {
        try {
            final String cacheKey = buildCacheKey(userId, idempotencyKey);

            redisTemplate.opsForValue().set(
                    cacheKey,
                    response,
                    ttl);
        } catch (DataAccessException | SerializationException exception) {
            LOGGER.warn(
                    "Idempotency cache write failed for userId={}; database operation remains successful",
                    userId,
                    exception);
        }
    }

    private String buildCacheKey(
            final Long userId,
            final String idempotencyKey) {
        return CACHE_KEY_PREFIX + userId + ":" + idempotencyKey;
    }


    public void evict(final Long userId, final String idempotencyKey) {
        redisTemplate.delete(buildCacheKey(userId, idempotencyKey));
    }

}