package com.nuclei.orderservice.cache;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.enums.OrderStatusEnum;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;



@ExtendWith(MockitoExtension.class)
class OrderIdempotencyCacheTest {

    private static final Long USER_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "test-idempotency-key";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock
    private RedisTemplate<String, OrderResponseDto> redisTemplate;

    @Mock
    private ValueOperations<String, OrderResponseDto> valueOperations;

    private OrderIdempotencyCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new OrderIdempotencyCache(redisTemplate, TTL);
    }

    @Test
    void getShouldReturnCachedResponseWhenKeyExists() {
        final OrderResponseDto response = createResponse();

        when(valueOperations.get(buildKey(USER_ID, IDEMPOTENCY_KEY)))
                .thenReturn(response);

        final Optional<OrderResponseDto> result =
                cache.get(USER_ID, IDEMPOTENCY_KEY);

        assertEquals(Optional.of(response), result);
        verify(valueOperations).get(buildKey(USER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void getShouldReturnEmptyWhenKeyDoesNotExist() {
        when(valueOperations.get(buildKey(USER_ID, IDEMPOTENCY_KEY)))
                .thenReturn(null);

        final Optional<OrderResponseDto> result =
                cache.get(USER_ID, IDEMPOTENCY_KEY);

        assertTrue(result.isEmpty());
        verify(valueOperations).get(buildKey(USER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void getShouldReturnEmptyWhenRedisConnectionFails() {
        when(valueOperations.get(buildKey(USER_ID, IDEMPOTENCY_KEY)))
                .thenThrow(new RedisConnectionFailureException(
                        "Redis unavailable"));

        final Optional<OrderResponseDto> result =
                cache.get(USER_ID, IDEMPOTENCY_KEY);

        assertTrue(result.isEmpty());
        verify(valueOperations).get(buildKey(USER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void getShouldReturnEmptyWhenSerializationFails() {
        when(valueOperations.get(buildKey(USER_ID, IDEMPOTENCY_KEY)))
                .thenThrow(new SerializationException(
                        "Serialization failed"));

        final Optional<OrderResponseDto> result =
                cache.get(USER_ID, IDEMPOTENCY_KEY);

        assertTrue(result.isEmpty());
        verify(valueOperations).get(buildKey(USER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void putShouldStoreResponseWithConfiguredTtl() {
        final OrderResponseDto response = createResponse();

        cache.put(USER_ID, IDEMPOTENCY_KEY, response);

        verify(valueOperations).set(
                buildKey(USER_ID, IDEMPOTENCY_KEY),
                response,
                TTL);
    }

    @Test
    void putShouldNotPropagateRedisFailure() {
        final OrderResponseDto response = createResponse();

        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(valueOperations)
                .set(
                        buildKey(USER_ID, IDEMPOTENCY_KEY),
                        response,
                        TTL);

        cache.put(USER_ID, IDEMPOTENCY_KEY, response);

        verify(valueOperations).set(
                buildKey(USER_ID, IDEMPOTENCY_KEY),
                response,
                TTL);
    }

    @Test
    void cacheKeyShouldIncludeUserIdAndIdempotencyKey() {
        final Long anotherUserId = 2L;
        final String anotherIdempotencyKey = "another-key";

        final OrderResponseDto firstResponse = createResponse();
        final OrderResponseDto secondResponse = createResponse();

        when(valueOperations.get(buildKey(USER_ID, IDEMPOTENCY_KEY)))
                .thenReturn(firstResponse);
        when(valueOperations.get(
                buildKey(anotherUserId, anotherIdempotencyKey)))
                .thenReturn(secondResponse);

        assertEquals(
                Optional.of(firstResponse),
                cache.get(USER_ID, IDEMPOTENCY_KEY));

        assertEquals(
                Optional.of(secondResponse),
                cache.get(anotherUserId, anotherIdempotencyKey));

        verify(valueOperations).get(buildKey(USER_ID, IDEMPOTENCY_KEY));
        verify(valueOperations).get(
                buildKey(anotherUserId, anotherIdempotencyKey));
    }

    private OrderResponseDto createResponse() {
        return new OrderResponseDto(
                100L,
                USER_ID,
                List.of(),
                new BigDecimal("200.00"),
                OrderStatusEnum.CONFIRMED);
    }

    private String buildKey(
            final Long userId,
            final String idempotencyKey) {
        return "order:idempotency:" + userId + ":" + idempotencyKey;
    }
}