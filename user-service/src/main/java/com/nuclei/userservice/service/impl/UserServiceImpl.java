package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.dto.UserCreateRequestDto;
import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.entity.User;
import com.nuclei.userservice.exception.UserAlreadyExistsException;
import com.nuclei.userservice.exception.UserNotFoundException;
import com.nuclei.userservice.mapper.UserMapper;
import com.nuclei.userservice.repo.UserRepository;
import com.nuclei.userservice.service.IIdempotencyService;
import com.nuclei.userservice.service.IUserService;
import com.nuclei.userservice.service.RedisEmailLockService;
import com.nuclei.userservice.util.EmailEncryptionUtil;
import com.nuclei.userservice.util.EmailUtil;
import com.nuclei.userservice.util.RequestHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final EmailUtil emailUtil;
    private final EmailEncryptionUtil emailEncryptionUtil;
    private final RequestHashUtil requestHashUtil;
    private final IIdempotencyService idempotencyService;
    private final RedisEmailLockService redisEmailLockService;

    private static final Logger LOG =
            LoggerFactory.getLogger(UserServiceImpl.class);

    public UserServiceImpl(
            final UserRepository userRepository,
            final UserMapper userMapper,
            final EmailUtil emailUtil,
            final EmailEncryptionUtil emailEncryptionUtil,
            final RequestHashUtil requestHashUtil,
            final IIdempotencyService idempotencyService,
            final RedisEmailLockService redisEmailLockService) {

        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.emailUtil = emailUtil;
        this.emailEncryptionUtil = emailEncryptionUtil;
        this.requestHashUtil = requestHashUtil;
        this.idempotencyService = idempotencyService;
        this.redisEmailLockService = redisEmailLockService;
    }

    @Override
    public UserResponseDto createUser(
            final UserCreateRequestDto request,
            final String idempotencyKey) {

        final String normalizedEmail =
                emailUtil.normalize(request.email());

        final String requestHash =
                requestHashUtil.generateHash(
                        request.name(),
                        normalizedEmail
                );

        final UserResponseDto existingUser =
                findUserByIdempotencyKey(
                        idempotencyKey,
                        requestHash
                );

        if (existingUser != null) {
            return existingUser;
        }

        final String lockValue =
                redisEmailLockService.acquireLock(normalizedEmail);

        try {
            final UserResponseDto existingUserAfterLock =
                    findUserByIdempotencyKey(idempotencyKey,requestHash);

            if (existingUserAfterLock != null) {
                return existingUserAfterLock;
            }

            final String encryptedEmail =
                    emailEncryptionUtil.encrypt(normalizedEmail);

            final UserResponseDto existingUserByEmail =
                    findExistingUserByEmail(
                            encryptedEmail,
                            normalizedEmail
                    );

            if (existingUserByEmail != null) {
                return existingUserByEmail;
            }

            final User user = userMapper.toEntity(
                   request.name(),
                    normalizedEmail,
                    request.password()
            );

            final User savedUser = saveUser(user, normalizedEmail);

            return saveIdempotencyResultAndReturnUser(
                    idempotencyKey,
                    requestHash,
                    savedUser
            );

        } finally {
            redisEmailLockService.releaseLock(
                    normalizedEmail,
                    lockValue
            );
        }
    }

    private UserResponseDto findUserByIdempotencyKey(
            final String idempotencyKey,
            final String requestHash) {

        final String existingUserId =
                idempotencyService.getResult(
                        idempotencyKey,
                        requestHash
                );

        if (existingUserId == null) {
            return null;
        }

        return getUser(Long.valueOf(existingUserId));
    }

    private UserResponseDto findExistingUserByEmail(
            final String encryptedEmail,
            final String normalizedEmail) {

        return userRepository.findByEmail(encryptedEmail)
                .map(user -> {
                    LOG.warn(
                            "User already exists with email: {}",
                            normalizedEmail
                    );
                    return userMapper.toResponseDto(user);
                })
                .orElse(null);
    }

    private User saveUser(
            final User user,
            final String normalizedEmail) {

        try {
            return userRepository.save(user);
        } catch (final DataIntegrityViolationException exception) {
            throw new UserAlreadyExistsException(
                    "User already exists with email: " + normalizedEmail,
                    exception
            );
        }
    }

    private UserResponseDto saveIdempotencyResultAndReturnUser(
            final String idempotencyKey,
            final String requestHash,
            final User savedUser) {

        final String idempotencyResult =
                idempotencyService.saveResult(
                        idempotencyKey,
                        requestHash,
                        savedUser.getId().toString()
                );

        return getUser(Long.valueOf(idempotencyResult));
    }

    @Override
    public UserResponseDto getUser(final Long userId) {

        final User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        return userMapper.toResponseDto(user);
    }
}