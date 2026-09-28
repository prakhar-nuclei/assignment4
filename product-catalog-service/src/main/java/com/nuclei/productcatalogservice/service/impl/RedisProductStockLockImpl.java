package com.nuclei.productcatalogservice.service.impl;

import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.service.ProductStockLock;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisProductStockLockImpl implements ProductStockLock {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private static final String LOCK_KEY_PREFIX = "product:stock:lock:";

    private static final String RELEASE_LOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private final StringRedisTemplate redisTemplate;

    public RedisProductStockLockImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String acquire(Long productId) {
        String lockKey = buildLockKey(productId);
        String lockToken = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            throw new ProductConcurrencyException(productId);
        }

        return lockToken;
    }

    @Override
    public void release(Long productId, String lockToken) {
        String lockKey = buildLockKey(productId);

        redisTemplate.execute(
                new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class),
                Collections.singletonList(lockKey),
                lockToken
        );
    }

    private String buildLockKey(Long productId) {
        return LOCK_KEY_PREFIX + productId;
    }
}