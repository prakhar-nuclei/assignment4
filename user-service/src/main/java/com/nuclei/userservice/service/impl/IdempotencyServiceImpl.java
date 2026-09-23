package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.service.IIdempotencyService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyServiceImpl implements IIdempotencyService {

    private static final String KEY_PREFIX =
            "user:create:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final Duration expiry;

    public IdempotencyServiceImpl(
            final StringRedisTemplate redisTemplate,
            @Value("${spring.data.redis.idempotency-expiry-seconds}")
            final long expirySeconds) {

        this.redisTemplate = redisTemplate;
        this.expiry = Duration.ofSeconds(expirySeconds);
    }

    @Override
    public String getResult(final String idempotencyKey) {

        final String key = KEY_PREFIX + idempotencyKey;

        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean saveResult(
            final String idempotencyKey,
            final String result) {

        final String key = KEY_PREFIX + idempotencyKey;

        final Boolean saved =
                redisTemplate.opsForValue()
                        .setIfAbsent(
                                key,
                                result,
                                expiry
                        );

        return Boolean.TRUE.equals(saved);
    }
}