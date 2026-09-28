package com.nuclei.productcatalogservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisProductStockLockImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisProductStockLockImpl productStockLock;

    @BeforeEach
    void setUp() {
        productStockLock = new RedisProductStockLockImpl(redisTemplate);
    }

    @Test
    void acquireShouldReturnLockTokenWhenLockIsAvailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq("product:stock:lock:1"),
                any(String.class),
                any(Duration.class)
        )).thenReturn(true);

        String lockToken = productStockLock.acquire(1L);

        assertNotNull(lockToken);
        verify(valueOperations).setIfAbsent(
                eq("product:stock:lock:1"),
                eq(lockToken),
                any(Duration.class)
        );
    }

    @Test
    void acquireShouldThrowExceptionWhenLockIsAlreadyHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq("product:stock:lock:1"),
                any(String.class),
                any(Duration.class)
        )).thenReturn(false);

        assertThrows(
                ProductConcurrencyException.class,
                () -> productStockLock.acquire(1L)
        );

        verify(valueOperations).setIfAbsent(
                eq("product:stock:lock:1"),
                any(String.class),
                any(Duration.class)
        );
    }

    @Test
    void acquireShouldThrowExceptionWhenRedisReturnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.setIfAbsent(
                eq("product:stock:lock:1"),
                any(String.class),
                any(Duration.class)
        )).thenReturn(null);

        assertThrows(
                ProductConcurrencyException.class,
                () -> productStockLock.acquire(1L)
        );
    }

    @Test
    void releaseShouldExecuteReleaseScriptWithProductLockKeyAndToken() {
        String lockToken = "lock-token";

        productStockLock.release(1L, lockToken);

        verify(redisTemplate).execute(
                any(),
                eq(Collections.singletonList("product:stock:lock:1")),
                eq(lockToken)
        );
    }
}