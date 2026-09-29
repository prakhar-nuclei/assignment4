package com.nuclei.userservice.service;

import com.nuclei.userservice.exception.RedisLockAcquisitionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service

public class RedisEmailLockService {

    private static final String LOCK_KEY_PREFIX =
            "user:create:lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final Duration lockExpiry;

    public RedisEmailLockService(
            final StringRedisTemplate redisTemplate,
            @Value("${spring.data.redis.lock-expiry-seconds}")
            final long lockExpirySeconds) {

        this.redisTemplate = redisTemplate;
        this.lockExpiry = Duration.ofSeconds(lockExpirySeconds);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public String acquireLock(final String normalizedEmail) {

        final String hashedEmail =
                hashEmail(normalizedEmail);

        final String lockKey =
                LOCK_KEY_PREFIX + hashedEmail;

        final String lockValue =
                UUID.randomUUID().toString();

        try {
            final Boolean acquired =
                    redisTemplate.opsForValue()
                            .setIfAbsent(
                                    lockKey,
                                    lockValue,
                                    lockExpiry
                            );

            if (!Boolean.TRUE.equals(acquired)) {
                log.warn("Unable to acquire Redis lock for email");
                throw new RedisLockAcquisitionException(
                        "Unable to acquire lock for email"
                );
            }

            return lockValue;

        } catch (final RedisLockAcquisitionException exception) {
            throw exception;

        } catch (final RuntimeException exception) {
            log.error("Failed to acquire Redis lock", exception);
            throw new RedisLockAcquisitionException(
                    "Failed to acquire Redis lock",
                    exception
            );
        }
    }

    public void releaseLock(
            final String normalizedEmail,
            final String lockValue) {

        if (lockValue == null) {
            return;
        }

        final String hashedEmail =
                hashEmail(normalizedEmail);

        final String lockKey =
                LOCK_KEY_PREFIX + hashedEmail;

        redisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                Collections.singletonList(lockKey),
                lockValue
        );
    }

    private String hashEmail(final String normalizedEmail) {
        try {
            final MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            final byte[] hash =
                    digest.digest(
                            normalizedEmail.getBytes(StandardCharsets.UTF_8)
                    );

            final StringBuilder result = new StringBuilder();

            for (final byte value : hash) {
                result.append(String.format("%02x", value));
            }

            return result.toString();

        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }
}