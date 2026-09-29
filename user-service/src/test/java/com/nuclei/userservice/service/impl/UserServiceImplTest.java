package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.dto.UserCreateRequestDto;
import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.entity.User;
import com.nuclei.userservice.exception.RedisLockAcquisitionException;
import com.nuclei.userservice.exception.UserAlreadyExistsException;
import com.nuclei.userservice.exception.UserNotFoundException;
import com.nuclei.userservice.mapper.UserMapper;
import com.nuclei.userservice.repo.UserRepository;
import com.nuclei.userservice.service.IIdempotencyService;
import com.nuclei.userservice.service.RedisEmailLockService;
import com.nuclei.userservice.util.EmailEncryptionUtil;
import com.nuclei.userservice.util.EmailUtil;
import com.nuclei.userservice.util.RequestHashUtil;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private EmailUtil emailUtil;

    @Mock
    private EmailEncryptionUtil emailEncryptionUtil;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RequestHashUtil requestHashUtil;

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
        final String idempotencyKey = "idempotency-key";
        final String requestHash = "request-hash";
        final String lockValue = "lock-value";

        final UserCreateRequestDto request =
                new UserCreateRequestDto(
                        name,
                        email,
                        password
                );

        final User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);

        final User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setName(name);
        savedUser.setEmail(normalizedEmail);

        final UserResponseDto userResponse =
                new UserResponseDto(
                        1L,
                        name,
                        normalizedEmail
                );

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(requestHashUtil.generateHash(
                name,
                normalizedEmail
        )).thenReturn(requestHash);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(null);

        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);

        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(userRepository.findByEmail(encryptedEmail))
                .thenReturn(java.util.Optional.empty());

        when(userMapper.toEntity(
                name,
                normalizedEmail,
                password
        )).thenReturn(user);

        when(userRepository.save(user))
                .thenReturn(savedUser);

        when(idempotencyService.saveResult(
                idempotencyKey,
                requestHash,
                savedUser.getId().toString()
        )).thenReturn(savedUser.getId().toString());

        when(userRepository.findById(savedUser.getId()))
                .thenReturn(java.util.Optional.of(savedUser));

        when(userMapper.toResponseDto(savedUser))
                .thenReturn(userResponse);

        final UserResponseDto result =
                userService.createUser(
                        request,
                        idempotencyKey
                );

        assertNotNull(result);
        assertEquals(savedUser.getId(), result.userId());
        assertEquals(savedUser.getName(), result.name());
        assertEquals(savedUser.getEmail(), result.email());

        verify(emailUtil).normalize(email);

        verify(requestHashUtil).generateHash(
                name,
                normalizedEmail
        );

        verify(idempotencyService, times(2)).getResult(
                idempotencyKey,
                requestHash
        );

        verify(redisEmailLockService).acquireLock(
                normalizedEmail
        );

        verify(emailEncryptionUtil).encrypt(
                normalizedEmail
        );

        verify(userRepository).findByEmail(
                encryptedEmail
        );

        verify(userMapper).toEntity(
                name,
                normalizedEmail,
                password
        );

        verify(userRepository).save(user);

        verify(idempotencyService).saveResult(
                idempotencyKey,
                requestHash,
                savedUser.getId().toString()
        );

        verify(userRepository).findById(
                savedUser.getId()
        );

        verify(userMapper).toResponseDto(
                savedUser
        );

        verify(redisEmailLockService).releaseLock(
                normalizedEmail,
                lockValue
        );
    }

    @Test
    void createUser_shouldReturnExistingUserForExistingIdempotencyKey() {
        final String name = "Prakhar";
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String password = "Password@123";
        final String idempotencyKey = "existing-key";
        final String requestHash = "request-hash";
        final Long existingUserId = 1L;

        final UserCreateRequestDto request =
                new UserCreateRequestDto(
                        name,
                        email,
                        password
                );

        final User existingUser = new User();
        existingUser.setId(existingUserId);
        existingUser.setName(name);
        existingUser.setEmail(normalizedEmail);

        final UserResponseDto userResponse =
                new UserResponseDto(
                        existingUserId,
                        name,
                        normalizedEmail
                );

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(requestHashUtil.generateHash(
                name,
                normalizedEmail
        )).thenReturn(requestHash);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(existingUserId.toString());

        when(userRepository.findById(existingUserId))
                .thenReturn(java.util.Optional.of(existingUser));

        when(userMapper.toResponseDto(existingUser))
                .thenReturn(userResponse);

        final UserResponseDto result =
                userService.createUser(
                        request,
                        idempotencyKey
                );

        assertNotNull(result);
        assertEquals(existingUserId, result.userId());
        assertEquals(existingUser.getName(), result.name());
        assertEquals(existingUser.getEmail(), result.email());

        verify(emailUtil).normalize(email);

        verify(requestHashUtil).generateHash(
                name,
                normalizedEmail
        );

        verify(idempotencyService).getResult(
                idempotencyKey,
                requestHash
        );

        verify(userRepository).findById(
                existingUserId
        );

        verify(userMapper).toResponseDto(
                existingUser
        );

        verifyNoInteractions(
                emailEncryptionUtil,
                redisEmailLockService
        );

        verify(userRepository, never())
                .save(any(User.class));

        verify(userMapper, never())
                .toEntity(
                        anyString(),
                        anyString(),
                        anyString()
                );
    }

    @Test
    void createUser_shouldReturnExistingUserWhenEmailAlreadyExists() {
        final String name = "Prakhar";
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "Password@123";
        final String idempotencyKey = "idempotency-key";
        final String requestHash = "request-hash";
        final String lockValue = "lock-value";

        final User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setName(name);
        existingUser.setEmail(normalizedEmail);

        final UserResponseDto existingUserResponse =
                new UserResponseDto(
                        1L,
                        name,
                        normalizedEmail
                );

        final UserCreateRequestDto request =
                new UserCreateRequestDto(
                        name,
                        email,
                        password
                );

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(requestHashUtil.generateHash(
                name,
                normalizedEmail
        )).thenReturn(requestHash);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(null);

        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(null);

        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(userRepository.findByEmail(encryptedEmail))
                .thenReturn(java.util.Optional.of(existingUser));

        when(userMapper.toResponseDto(existingUser))
                .thenReturn(existingUserResponse);

        final UserResponseDto result =
                userService.createUser(
                        request,
                        idempotencyKey
                );

        assertNotNull(result);
        assertEquals(
                existingUserResponse.userId(),
                result.userId()
        );
        assertEquals(
                existingUserResponse.name(),
                result.name()
        );
        assertEquals(
                existingUserResponse.email(),
                result.email()
        );

        verify(emailUtil).normalize(email);

        verify(requestHashUtil).generateHash(
                name,
                normalizedEmail
        );

        verify(idempotencyService, times(2)).getResult(
                idempotencyKey,
                requestHash
        );

        verify(redisEmailLockService).acquireLock(
                normalizedEmail
        );

        verify(emailEncryptionUtil).encrypt(
                normalizedEmail
        );

        verify(userRepository).findByEmail(
                encryptedEmail
        );

        verify(userMapper).toResponseDto(
                existingUser
        );

        verify(userRepository, never())
                .save(any(User.class));

        verify(userMapper, never())
                .toEntity(
                        anyString(),
                        anyString(),
                        anyString()
                );

        verify(redisEmailLockService).releaseLock(
                normalizedEmail,
                lockValue
        );
    }

    @Test
    void createUser_shouldThrowExceptionWhenLockAcquisitionFails() {
        final String name = "Prakhar";
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String password = "Password@123";
        final String idempotencyKey = "idempotency-key";
        final String requestHash = "request-hash";

        final UserCreateRequestDto request =
                new UserCreateRequestDto(
                        name,
                        email,
                        password
                );

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(requestHashUtil.generateHash(
                name,
                normalizedEmail
        )).thenReturn(requestHash);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(null);

        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenThrow(
                        new RedisLockAcquisitionException(
                                "Failed to acquire Redis lock"
                        )
                );

        assertThrows(
                RedisLockAcquisitionException.class,
                () -> userService.createUser(
                        request,
                        idempotencyKey
                )
        );

        verify(emailUtil).normalize(email);

        verify(requestHashUtil).generateHash(
                name,
                normalizedEmail
        );

        verify(idempotencyService).getResult(
                idempotencyKey,
                requestHash
        );

        verify(redisEmailLockService).acquireLock(
                normalizedEmail
        );

        verifyNoInteractions(
                emailEncryptionUtil,
                userMapper
        );

        verify(userRepository, never())
                .findByEmail(anyString());

        verify(userRepository, never())
                .save(any(User.class));
    }

    @Test
    void createUser_shouldThrowExceptionWhenDatabaseRejectsDuplicateEmail() {
        final String name = "Prakhar";
        final String email = "Prakhar@Example.com";
        final String normalizedEmail = "prakhar@example.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "Password@123";
        final String idempotencyKey = "idempotency-key";
        final String requestHash = "request-hash";
        final String lockValue = "lock-value";

        final UserCreateRequestDto request =
                new UserCreateRequestDto(
                        name,
                        email,
                        password
                );

        final User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(requestHashUtil.generateHash(
                name,
                normalizedEmail
        )).thenReturn(requestHash);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(null);

        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(null);

        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(userRepository.findByEmail(encryptedEmail))
                .thenReturn(java.util.Optional.empty());

        when(userMapper.toEntity(
                name,
                normalizedEmail,
                password
        )).thenReturn(user);

        when(userRepository.save(user))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Duplicate email"
                        )
                );

        assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.createUser(
                        request,
                        idempotencyKey
                )
        );

        verify(emailUtil).normalize(email);

        verify(requestHashUtil).generateHash(
                name,
                normalizedEmail
        );

        verify(idempotencyService, times(2)).getResult(
                idempotencyKey,
                requestHash
        );

        verify(redisEmailLockService).acquireLock(
                normalizedEmail
        );

        verify(emailEncryptionUtil).encrypt(
                normalizedEmail
        );

        verify(userRepository).findByEmail(
                encryptedEmail
        );

        verify(userMapper).toEntity(
                name,
                normalizedEmail,
                password
        );

        verify(userRepository).save(user);

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
        final String idempotencyKey = "idempotency-key";
        final String requestHash = "request-hash";
        final String lockValue = "lock-value";
        final Long savedUserId = 1L;
        final Long existingUserId = 2L;

        final UserCreateRequestDto request =
                new UserCreateRequestDto(
                        name,
                        email,
                        password
                );

        final User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);

        final User savedUser = new User();
        savedUser.setId(savedUserId);
        savedUser.setName(name);
        savedUser.setEmail(normalizedEmail);

        final User existingUser = new User();
        existingUser.setId(existingUserId);
        existingUser.setName("Existing User");
        existingUser.setEmail("existing@example.com");

        final UserResponseDto existingUserResponse =
                new UserResponseDto(
                        existingUserId,
                        "Existing User",
                        "existing@example.com"
                );

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(requestHashUtil.generateHash(
                name,
                normalizedEmail
        )).thenReturn(requestHash);

        when(idempotencyService.getResult(
                idempotencyKey,
                requestHash
        )).thenReturn(
                null,
                null
        );

        when(redisEmailLockService.acquireLock(normalizedEmail))
                .thenReturn(lockValue);

        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(userRepository.findByEmail(encryptedEmail))
                .thenReturn(java.util.Optional.empty());

        when(userMapper.toEntity(
                name,
                normalizedEmail,
                password
        )).thenReturn(user);

        when(userRepository.save(user))
                .thenReturn(savedUser);

        when(idempotencyService.saveResult(
                idempotencyKey,
                requestHash,
                savedUserId.toString()
        )).thenReturn(existingUserId.toString());

        when(userRepository.findById(existingUserId))
                .thenReturn(java.util.Optional.of(existingUser));

        when(userMapper.toResponseDto(existingUser))
                .thenReturn(existingUserResponse);

        final UserResponseDto result =
                userService.createUser(
                        request,
                        idempotencyKey
                );

        assertNotNull(result);
        assertEquals(
                existingUserId,
                result.userId()
        );
        assertEquals(
                existingUser.getName(),
                result.name()
        );
        assertEquals(
                existingUser.getEmail(),
                result.email()
        );

        verify(idempotencyService, times(2)).getResult(
                idempotencyKey,
                requestHash
        );

        verify(redisEmailLockService).acquireLock(
                normalizedEmail
        );

        verify(emailEncryptionUtil).encrypt(
                normalizedEmail
        );

        verify(userRepository).findByEmail(
                encryptedEmail
        );

        verify(userMapper).toEntity(
                name,
                normalizedEmail,
                password
        );

        verify(userRepository).save(user);

        verify(idempotencyService).saveResult(
                idempotencyKey,
                requestHash,
                savedUserId.toString()
        );

        verify(userRepository).findById(
                existingUserId
        );

        verify(userMapper).toResponseDto(
                existingUser
        );

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

        final UserResponseDto userResponse =
                new UserResponseDto(
                        userId,
                        "Prakhar",
                        "prakhar@example.com"
                );

        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(user));

        when(userMapper.toResponseDto(user))
                .thenReturn(userResponse);

        final UserResponseDto result =
                userService.getUser(userId);

        assertNotNull(result);
        assertEquals(
                userResponse.userId(),
                result.userId()
        );
        assertEquals(
                userResponse.name(),
                result.name()
        );
        assertEquals(
                userResponse.email(),
                result.email()
        );

        verify(userRepository).findById(userId);

        verify(userMapper).toResponseDto(user);
    }

    @Test
    void getUser_shouldThrowExceptionWhenUserDoesNotExist() {
        final Long userId = 999L;

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getUser(userId)
        );

        verify(userRepository).findById(userId);

        verifyNoInteractions(userMapper);
    }
}