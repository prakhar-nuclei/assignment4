package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.entity.User;
import com.nuclei.userservice.exception.IdempotencyException;
import com.nuclei.userservice.exception.UserAlreadyExistsException;
import com.nuclei.userservice.exception.UserNotFoundException;
import com.nuclei.userservice.repo.UserRepository;
import com.nuclei.userservice.service.IIdempotencyService;
import com.nuclei.userservice.service.IUserService;
import com.nuclei.userservice.service.RedisEmailLockService;
import com.nuclei.userservice.util.EmailEncryptionUtil;
import com.nuclei.userservice.util.EmailUtil;
import com.nuclei.userservice.util.PasswordUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final PasswordUtil passwordUtil;
    private final EmailUtil emailUtil;
    private final EmailEncryptionUtil emailEncryptionUtil;
    private final IIdempotencyService idempotencyService;
    private final RedisEmailLockService redisEmailLockService;

    public UserServiceImpl(
            final UserRepository userRepository,
            final PasswordUtil passwordUtil,
            final EmailUtil emailUtil,
            final EmailEncryptionUtil emailEncryptionUtil,
            final IIdempotencyService idempotencyService,
            final RedisEmailLockService redisEmailLockService) {

        this.userRepository = userRepository;
        this.passwordUtil = passwordUtil;
        this.emailUtil = emailUtil;
        this.emailEncryptionUtil = emailEncryptionUtil;
        this.idempotencyService = idempotencyService;
        this.redisEmailLockService = redisEmailLockService;
    }

    @Override
    public UserResponseDto createUser(
            final String name,
            final String email,
            final String password,
            final String idempotencyKey) {

        final UserResponseDto existingUser =
                findUserByIdempotencyKey(idempotencyKey);

        if (existingUser != null) {
            return existingUser;
        }

        final String normalizedEmail = emailUtil.normalize(email);
        final String lockValue =
                redisEmailLockService.acquireLock(normalizedEmail);

        try {
            final UserResponseDto existingUserAfterLock =
                    findUserByIdempotencyKey(idempotencyKey);

            if (existingUserAfterLock != null) {
                return existingUserAfterLock;
            }

            final String encryptedEmail =
                    emailEncryptionUtil.encrypt(normalizedEmail);

            validateUserDoesNotExist(encryptedEmail, normalizedEmail);

            final User user = buildUser(
                    name,
                    normalizedEmail,
                    password
            );

            final User savedUser = saveUser(user, normalizedEmail);

            return saveIdempotencyResultAndReturnUser(
                    idempotencyKey,
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
            final String idempotencyKey) {

        final String existingUserId =
                idempotencyService.getResult(idempotencyKey);

        if (existingUserId == null) {
            return null;
        }

        return getUser(Long.valueOf(existingUserId));
    }

    private void validateUserDoesNotExist(
            final String encryptedEmail,
            final String normalizedEmail) {

        if (userRepository.existsByEmail(encryptedEmail)) {
            throw new UserAlreadyExistsException(
                    "User already exists with email: " + normalizedEmail
            );
        }
    }

    private User buildUser(
            final String name,
            final String normalizedEmail,
            final String password) {

        final String hashedPassword =
                passwordUtil.hash(password);

        final User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(hashedPassword);

        return user;
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
            final User savedUser) {

        final boolean resultSaved =
                idempotencyService.saveResult(
                        idempotencyKey,
                        savedUser.getId().toString()
                );

        if (resultSaved) {
            return toResponseDto(savedUser);
        }

        final String existingUserId =
                idempotencyService.getResult(idempotencyKey);

        if (existingUserId == null) {
            throw new IdempotencyException(
                    "Failed to save idempotency result"
            );
        }

        return getUser(Long.valueOf(existingUserId));
    }

    private UserResponseDto toResponseDto(final User user) {
        return new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

    @Override
    public UserResponseDto getUser(final Long userId) {

        final User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        return toResponseDto(user);
    }
}