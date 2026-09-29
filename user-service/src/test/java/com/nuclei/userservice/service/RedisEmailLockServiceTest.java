package com.nuclei.userservice.service;

import com.nuclei.userservice.exception.RedisLockAcquisitionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisEmailLockServiceTest {

    private static final String NORMALIZED_EMAIL =
            "test@email.com";

    private static final Duration LOCK_EXPIRY =
            Duration.ofSeconds(10L);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisEmailLockService redisEmailLockService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        redisEmailLockService =
                new RedisEmailLockService(
                        redisTemplate,
                        10L
                );
    }

    @Test
    void acquireLock_shouldReturnLockValueWhenLockIsAcquired() {
        final String lockKey =
                buildLockKey(NORMALIZED_EMAIL);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(LOCK_EXPIRY)
        )).thenReturn(true);

        final String lockValue =
                redisEmailLockService.acquireLock(
                        NORMALIZED_EMAIL
                );

        assertNotNull(lockValue);

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                eq(lockKey),
                eq(lockValue),
                eq(LOCK_EXPIRY)
        );
    }

    @Test
    void acquireLock_shouldThrowExceptionWhenLockAlreadyExists() {
        final String lockKey =
                buildLockKey(NORMALIZED_EMAIL);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(LOCK_EXPIRY)
        )).thenReturn(false);

        assertThrows(
                RedisLockAcquisitionException.class,
                () -> redisEmailLockService.acquireLock(
                        NORMALIZED_EMAIL
                )
        );

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(LOCK_EXPIRY)
        );
    }

    @Test
    void acquireLock_shouldThrowExceptionWhenRedisFails() {
        final String lockKey =
                buildLockKey(NORMALIZED_EMAIL);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(LOCK_EXPIRY)
        )).thenThrow(
                new RuntimeException("Redis unavailable")
        );

        assertThrows(
                RedisLockAcquisitionException.class,
                () -> redisEmailLockService.acquireLock(
                        NORMALIZED_EMAIL
                )
        );

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(LOCK_EXPIRY)
        );
    }

    @Test
    void acquireLock_shouldThrowExceptionWhenRedisReturnsNull() {
        final String lockKey =
                buildLockKey(NORMALIZED_EMAIL);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(LOCK_EXPIRY)
        )).thenReturn(null);

        assertThrows(
                RedisLockAcquisitionException.class,
                () -> redisEmailLockService.acquireLock(
                        NORMALIZED_EMAIL
                )
        );

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(LOCK_EXPIRY)
        );
    }

    @Test
    void releaseLock_shouldExecuteReleaseScript() {
        final String lockValue = "lock-value-123";
        final String lockKey =
                buildLockKey(NORMALIZED_EMAIL);

        redisEmailLockService.releaseLock(
                NORMALIZED_EMAIL,
                lockValue
        );

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList(lockKey)),
                eq(lockValue)
        );
    }

    @Test
    void releaseLock_shouldNotExecuteScriptWhenLockValueIsNull() {
        redisEmailLockService.releaseLock(
                NORMALIZED_EMAIL,
                null
        );

        verify(redisTemplate, never()).execute(
                any(DefaultRedisScript.class),
                any(),
                anyString()
        );
    }

    private String buildLockKey(final String normalizedEmail) {
        return "user:create:lock:"
                + hashEmail(normalizedEmail);
    }

    private String hashEmail(final String email) {
        try {
            final MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            final byte[] hash =
                    digest.digest(
                            email.getBytes(StandardCharsets.UTF_8)
                    );

            final StringBuilder result =
                    new StringBuilder();

            for (final byte value : hash) {
                result.append(
                        String.format("%02x", value)
                );
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