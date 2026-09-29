package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.entity.UserIdempotency;
import com.nuclei.userservice.exception.IdempotencyKeyConflictException;
import com.nuclei.userservice.repo.UserIdempotencyRepository;
import com.nuclei.userservice.service.IIdempotencyService;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyServiceImpl implements IIdempotencyService {

    private static final String KEY_PREFIX =
            "user:create:idempotency:";

    private static final int CACHED_RESULT_PARTS = 2;

    private static final Logger LOG =
            LoggerFactory.getLogger(IdempotencyServiceImpl.class);

    private final StringRedisTemplate redisTemplate;
    private final UserIdempotencyRepository userIdempotencyRepository;
    private final Duration expiry;

    public IdempotencyServiceImpl(
            final StringRedisTemplate redisTemplate,
            final UserIdempotencyRepository userIdempotencyRepository,
            @Value("${spring.data.redis.idempotency-expiry-seconds}")
            final long expirySeconds) {

        this.redisTemplate = redisTemplate;
        this.userIdempotencyRepository = userIdempotencyRepository;
        this.expiry = Duration.ofSeconds(expirySeconds);
    }

    @Override
    public String getResult(
            final String idempotencyKey,
            final String requestHash) {

        final String key =
                KEY_PREFIX + idempotencyKey;

        try {
            final String cachedValue =
                    redisTemplate.opsForValue().get(key);

            if (cachedValue != null) {
                final String cachedResult =
                        validateCachedResult(
                                cachedValue,
                                requestHash
                        );

                if (cachedResult != null) {
                    return cachedResult;
                }
            }
        } catch (final RedisSystemException exception) {
            LOG.warn(
                    "Failed to cache idempotency result in Redis",
                    exception
            );
        }

        return findResultFromDatabase(
                idempotencyKey,
                requestHash
        );
    }

    @Override
    public String saveResult(
            final String idempotencyKey,
            final String requestHash,
            final String result) {

        final UserIdempotency idempotency =
                new UserIdempotency();

        idempotency.setIdempotencyKey(idempotencyKey);
        idempotency.setRequestHash(requestHash);
        idempotency.setUserId(Long.valueOf(result));

        try {
            userIdempotencyRepository.saveAndFlush(idempotency);

            try {
                final String key =
                        KEY_PREFIX + idempotencyKey;

                final String value =
                        result + ":" + requestHash;

                redisTemplate.opsForValue()
                        .set(
                                key,
                                value,
                                expiry
                        );

            } catch (final RedisSystemException exception) {
                LOG.warn(
                        "Failed to cache idempotency result in Redis",
                        exception
                );
            }

            return result;

        } catch (final DataIntegrityViolationException exception) {

            final Optional<UserIdempotency> existing =
                    userIdempotencyRepository
                            .findByIdempotencyKey(idempotencyKey);

            if (existing.isEmpty()) {
                throw exception;
            }

            final UserIdempotency existingRecord =
                    existing.get();

            if (!existingRecord.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException(
                        "Idempotency key was already used for a different request",
                        exception
                );
            }

            return existingRecord.getUserId().toString();
        }
    }

    private String findResultFromDatabase(
            final String idempotencyKey,
            final String requestHash) {

        final Optional<UserIdempotency> idempotency =
                userIdempotencyRepository
                        .findByIdempotencyKey(idempotencyKey);

        if (idempotency.isEmpty()) {
            return null;
        }

        final UserIdempotency record =
                idempotency.get();

        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency key was already used for a different request"
            );
        }

        return record.getUserId().toString();
    }

    private String validateCachedResult(
            final String cachedValue,
            final String requestHash) {

        final String[] values =
                cachedValue.split(":", 2);

        if (values.length != CACHED_RESULT_PARTS) {
            return null;
        }
        
        final String cachedRequestHash = values[1];

        if (!cachedRequestHash.equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency key was already used for a different request"
            );
        }

        return values[0];
    }
}