package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.entity.UserIdempotency;
import com.nuclei.userservice.repo.UserIdempotencyRepository;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserIdempotencyRepository userIdempotencyRepository;

    private IdempotencyServiceImpl idempotencyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        idempotencyService =
                new IdempotencyServiceImpl(
                        redisTemplate,
                        userIdempotencyRepository,
                        86400L
                );
    }

    @Test
    void getResult_shouldReturnStoredResult() {
        final String idempotencyKey = "abc-123";
        final String requestHash = "hash-123";
        final String redisKey =
                "user:create:idempotency:abc-123";
        final String expectedResult = "15";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(redisKey))
                .thenReturn(expectedResult + ":" + requestHash);

        final String result =
                idempotencyService.getResult(
                        idempotencyKey,
                        requestHash
                );

        assertEquals(expectedResult, result);

        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(redisKey);
    }

    @Test
    void saveResult_shouldSaveResultSuccessfully() {
        final String idempotencyKey = "abc-123";
        final String requestHash = "hash-123";
        final String result = "15";

        final UserIdempotency idempotency =
                new UserIdempotency();
        idempotency.setIdempotencyKey(idempotencyKey);
        idempotency.setRequestHash(requestHash);
        idempotency.setUserId(Long.valueOf(result));

        when(userIdempotencyRepository.saveAndFlush(
                any(UserIdempotency.class)
        )).thenReturn(idempotency);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        final String saved =
                idempotencyService.saveResult(
                        idempotencyKey,
                        requestHash,
                        result
                );

        assertEquals(result, saved);

        verify(userIdempotencyRepository).saveAndFlush(
                any(UserIdempotency.class)
        );

        verify(redisTemplate).opsForValue();

        verify(valueOperations).set(
                "user:create:idempotency:abc-123",
                "15:hash-123",
                Duration.ofSeconds(86400L)
        );
    }

    @Test
    void saveResult_shouldReturnExistingResultWhenKeyAlreadyExists() {
        final String idempotencyKey = "abc-123";
        final String requestHash = "hash-123";
        final String result = "20";

        final UserIdempotency existingRecord =
                new UserIdempotency();
        existingRecord.setIdempotencyKey(idempotencyKey);
        existingRecord.setRequestHash(requestHash);
        existingRecord.setUserId(Long.valueOf(result));

        when(userIdempotencyRepository.saveAndFlush(
                any(UserIdempotency.class)
        )).thenThrow(new DataIntegrityViolationException(
                "Duplicate idempotency key"
        ));

        when(userIdempotencyRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.of(existingRecord));

        final String saved =
                idempotencyService.saveResult(
                        idempotencyKey,
                        requestHash,
                        result
                );

        assertEquals(result, saved);

        verify(userIdempotencyRepository).saveAndFlush(
                any(UserIdempotency.class)
        );

        verify(userIdempotencyRepository)
                .findByIdempotencyKey(idempotencyKey);
    }
}