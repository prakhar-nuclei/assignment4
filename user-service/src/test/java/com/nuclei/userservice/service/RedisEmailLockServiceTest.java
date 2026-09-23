package com.nuclei.userservice.service;

import com.nuclei.userservice.exception.RedisLockAcquisitionException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisEmailLockServiceTest {

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
        final String normalizedEmail = "test@email.com";
        final String lockKey =
                "user:create:lock:" + normalizedEmail;

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(Duration.ofSeconds(10L))
        )).thenReturn(true);

        final String lockValue =
                redisEmailLockService.acquireLock(
                        normalizedEmail
                );

        assertNotNull(lockValue);

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                eq(lockKey),
                eq(lockValue),
                eq(Duration.ofSeconds(10L))
        );
    }

    @Test
    void acquireLock_shouldThrowExceptionWhenLockAlreadyExists() {
        final String normalizedEmail = "test@email.com";
        final String lockKey =
                "user:create:lock:" + normalizedEmail;

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(Duration.ofSeconds(10L))
        )).thenReturn(false);

        assertThrows(
                RedisLockAcquisitionException.class,
                () -> redisEmailLockService.acquireLock(
                        normalizedEmail
                )
        );

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(Duration.ofSeconds(10L))
        );
    }

    @Test
    void acquireLock_shouldThrowExceptionWhenRedisFails() {
        final String normalizedEmail = "test@email.com";
        final String lockKey =
                "user:create:lock:" + normalizedEmail;

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(Duration.ofSeconds(10L))
        )).thenThrow(new RuntimeException("Redis unavailable"));

        assertThrows(
                RedisLockAcquisitionException.class,
                () -> redisEmailLockService.acquireLock(
                        normalizedEmail
                )
        );

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                eq(lockKey),
                anyString(),
                eq(Duration.ofSeconds(10L))
        );
    }

    @Test
    void releaseLock_shouldExecuteReleaseScript() {
        final String normalizedEmail = "test@email.com";
        final String lockValue = "lock-value-123";
        final String lockKey =
                "user:create:lock:" + normalizedEmail;

        redisEmailLockService.releaseLock(
                normalizedEmail,
                lockValue
        );

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList(lockKey)),
                eq(lockValue)
        );
    }
}