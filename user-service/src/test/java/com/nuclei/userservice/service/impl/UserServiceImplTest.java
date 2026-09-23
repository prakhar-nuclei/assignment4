package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.entity.User;
import com.nuclei.userservice.exception.RedisLockAcquisitionException;
import com.nuclei.userservice.exception.UserAlreadyExistsException;
import com.nuclei.userservice.exception.UserNotFoundException;
import com.nuclei.userservice.repo.UserRepository;
import com.nuclei.userservice.service.IIdempotencyService;
import com.nuclei.userservice.service.RedisEmailLockService;
import com.nuclei.userservice.util.EmailEncryptionUtil;
import com.nuclei.userservice.util.EmailUtil;
import com.nuclei.userservice.util.PasswordUtil;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordUtil passwordUtil;

    @Mock
    private EmailUtil emailUtil;

    @Mock
    private EmailEncryptionUtil emailEncryptionUtil;

    @Mock
    private IIdempotencyService idempotencyService;

    @Mock
    private RedisEmailLockService redisEmailLockService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createUser_shouldCreateUserSuccessfully() {
        final String name = "Prakhar";
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "Password@123";
        final String hashedPassword = "hashed-password";
        final String idempotencyKey = "idempotency-key";
        final String lockValue = "lock-value";

        final User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setName(name);
        savedUser.setEmail(normalizedEmail);
        savedUser.setPasswordHash(hashedPassword);

        when(idempotencyService.getResult(idempotencyKey))
                .thenReturn(null);
        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);
        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);
        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);
        when(userRepository.existsByEmail(encryptedEmail))
                .thenReturn(false);
        when(passwordUtil.hash(password))
                .thenReturn(hashedPassword);
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenReturn(savedUser);
        when(idempotencyService.saveResult(
                idempotencyKey,
                savedUser.getId().toString()))
                .thenReturn(true);

        final UserResponseDto result = userService.createUser(
                name,
                email,
                password,
                idempotencyKey
        );

        assertNotNull(result);
        assertEquals(savedUser.getId(), result.userId());
        assertEquals(savedUser.getName(), result.name());
        assertEquals(savedUser.getEmail(), result.email());

        verify(emailUtil).normalize(email);
        verify(redisEmailLockService).acquireLock(normalizedEmail);
        verify(emailEncryptionUtil).encrypt(normalizedEmail);
        verify(userRepository).existsByEmail(encryptedEmail);
        verify(passwordUtil).hash(password);
        verify(userRepository).save(org.mockito.ArgumentMatchers.any(User.class));
        verify(idempotencyService).saveResult(
                idempotencyKey,
                savedUser.getId().toString());
        verify(redisEmailLockService).releaseLock(
                normalizedEmail,
                lockValue
        );
    }

    @Test
    void createUser_shouldReturnExistingUserForExistingIdempotencyKey() {
        final String name = "Prakhar";
        final String email = "Prakhar@Example.com";
        final String password = "Password@123";
        final String idempotencyKey = "existing-key";
        final Long existingUserId = 1L;

        final User existingUser = new User();
        existingUser.setId(existingUserId);
        existingUser.setName(name);
        existingUser.setEmail("prakhar@example.com");

        when(idempotencyService.getResult(idempotencyKey))
                .thenReturn(existingUserId.toString());
        when(userRepository.findById(existingUserId))
                .thenReturn(java.util.Optional.of(existingUser));

        final UserResponseDto result = userService.createUser(
                name,
                email,
                password,
                idempotencyKey
        );

        assertNotNull(result);
        assertEquals(existingUserId, result.userId());
        assertEquals(existingUser.getName(), result.name());
        assertEquals(existingUser.getEmail(), result.email());

        verify(idempotencyService).getResult(idempotencyKey);
        verify(userRepository).findById(existingUserId);

        verifyNoInteractions(
                emailUtil,
                passwordUtil,
                emailEncryptionUtil,
                redisEmailLockService
        );
    }

    @Test
    void createUser_shouldThrowExceptionWhenEmailAlreadyExists() {
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String encryptedEmail = "encrypted-email";
        final String idempotencyKey = "idempotency-key";
        final String lockValue = "lock-value";

        when(idempotencyService.getResult(idempotencyKey))
                .thenReturn(null);
        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);
        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);
        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);
        when(userRepository.existsByEmail(encryptedEmail))
                .thenReturn(true);

        assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.createUser(
                        "Prakhar",
                        email,
                        "Password@123",
                        idempotencyKey
                )
        );

        verify(userRepository).existsByEmail(encryptedEmail);
        verify(redisEmailLockService).releaseLock(
                normalizedEmail,
                lockValue
        );

        verifyNoInteractions(passwordUtil);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void createUser_shouldThrowExceptionWhenLockAcquisitionFails() {
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String idempotencyKey = "idempotency-key";

        when(idempotencyService.getResult(idempotencyKey))
                .thenReturn(null);
        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);
        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenThrow(new RedisLockAcquisitionException(
                        "Failed to acquire Redis lock"
                ));

        assertThrows(
                RedisLockAcquisitionException.class,
                () -> userService.createUser(
                        "Prakhar",
                        email,
                        "Password@123",
                        idempotencyKey
                )
        );

        verify(emailUtil).normalize(email);
        verify(redisEmailLockService).acquireLock(normalizedEmail);

        verifyNoInteractions(
                emailEncryptionUtil,
                passwordUtil
        );

        verify(userRepository, never())
                .existsByEmail(org.mockito.ArgumentMatchers.anyString());

        verify(userRepository, never())
                .save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void createUser_shouldThrowExceptionWhenDatabaseRejectsDuplicateEmail() {
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "Password@123";
        final String hashedPassword = "hashed-password";
        final String idempotencyKey = "idempotency-key";
        final String lockValue = "lock-value";

        when(idempotencyService.getResult(idempotencyKey))
                .thenReturn(null);
        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);
        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);
        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);
        when(userRepository.existsByEmail(encryptedEmail))
                .thenReturn(false);
        when(passwordUtil.hash(password))
                .thenReturn(hashedPassword);
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate email"
                ));

        assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.createUser(
                        "Prakhar",
                        email,
                        password,
                        idempotencyKey
                )
        );

        verify(userRepository).existsByEmail(encryptedEmail);
        verify(passwordUtil).hash(password);
        verify(userRepository).save(
                org.mockito.ArgumentMatchers.any(User.class)
        );

        verify(redisEmailLockService).releaseLock(
                normalizedEmail,
                lockValue
        );
    }

    @Test
    void createUser_shouldReturnExistingUserWhenIdempotencyResultAlreadySaved() {
        final String name = "Prakhar";
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "Password@123";
        final String hashedPassword = "hashed-password";
        final String idempotencyKey = "idempotency-key";
        final String lockValue = "lock-value";
        final Long existingUserId = 2L;

        final User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setName(name);
        savedUser.setEmail(normalizedEmail);
        savedUser.setPasswordHash(hashedPassword);

        final User existingUser = new User();
        existingUser.setId(existingUserId);
        existingUser.setName("Existing User");
        existingUser.setEmail("existing@example.com");

        when(idempotencyService.getResult(idempotencyKey))
                .thenReturn(
                        null,
                        null,
                        existingUserId.toString()
                );
        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);
        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);
        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);
        when(userRepository.existsByEmail(encryptedEmail))
                .thenReturn(false);
        when(passwordUtil.hash(password))
                .thenReturn(hashedPassword);
        when(userRepository.save(
                org.mockito.ArgumentMatchers.any(User.class)))
                .thenReturn(savedUser);
        when(idempotencyService.saveResult(
                idempotencyKey,
                savedUser.getId().toString()))
                .thenReturn(false);
        when(userRepository.findById(existingUserId))
                .thenReturn(java.util.Optional.of(existingUser));

        final UserResponseDto result = userService.createUser(
                name,
                email,
                password,
                idempotencyKey
        );

        assertNotNull(result);
        assertEquals(existingUserId, result.userId());
        assertEquals(existingUser.getName(), result.name());
        assertEquals(existingUser.getEmail(), result.email());

        verify(idempotencyService).saveResult(
                idempotencyKey,
                savedUser.getId().toString()
        );
        verify(userRepository).findById(existingUserId);
        verify(redisEmailLockService).releaseLock(
                normalizedEmail,
                lockValue
        );
    }

    @Test
    void getUser_shouldReturnUserSuccessfully() {
        final Long userId = 1L;
        final User user = new User();
        user.setId(userId);
        user.setName("Prakhar");
        user.setEmail("prakhar@example.com");

        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(user));

        final UserResponseDto result = userService.getUser(userId);

        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertEquals(user.getName(), result.name());
        assertEquals(user.getEmail(), result.email());

        verify(userRepository).findById(userId);
    }

    @Test
    void getUser_shouldThrowExceptionWhenUserDoesNotExist() {
        final Long userId = 999L;

        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getUser(userId)
        );

        verify(userRepository).findById(userId);
    }
}