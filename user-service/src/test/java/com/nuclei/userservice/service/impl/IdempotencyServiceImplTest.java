package com.nuclei.userservice.service.impl;

import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyServiceImpl idempotencyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        idempotencyService =
                new IdempotencyServiceImpl(
                        redisTemplate,
                        86400L
                );
    }

    @Test
    void getResult_shouldReturnStoredResult() {
        final String idempotencyKey = "abc-123";
        final String redisKey =
                "user:create:idempotency:abc-123";
        final String expectedResult = "15";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(redisKey))
                .thenReturn(expectedResult);

        final String result =
                idempotencyService.getResult(idempotencyKey);

        assertEquals(expectedResult, result);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(redisKey);
    }

    @Test
    void saveResult_shouldSaveResultSuccessfully() {
        final String idempotencyKey = "abc-123";
        final String result = "15";
        final String redisKey =
                "user:create:idempotency:abc-123";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(redisKey),
                eq(result),
                eq(Duration.ofSeconds(86400L))
        )).thenReturn(true);

        final boolean saved =
                idempotencyService.saveResult(
                        idempotencyKey,
                        result
                );

        assertEquals(true, saved);

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                redisKey,
                result,
                Duration.ofSeconds(86400L)
        );
    }

    @Test
    void saveResult_shouldReturnFalseWhenKeyAlreadyExists() {
        final String idempotencyKey = "abc-123";
        final String result = "20";
        final String redisKey =
                "user:create:idempotency:abc-123";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq(redisKey),
                eq(result),
                eq(Duration.ofSeconds(86400L))
        )).thenReturn(false);

        final boolean saved =
                idempotencyService.saveResult(
                        idempotencyKey,
                        result
                );

        assertEquals(false, saved);

        verify(redisTemplate).opsForValue();

        verify(valueOperations).setIfAbsent(
                redisKey,
                result,
                Duration.ofSeconds(86400L)
        );
    }
}