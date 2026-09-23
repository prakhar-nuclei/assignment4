package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.entity.User;
import com.nuclei.userservice.exception.AuthenticationException;
import com.nuclei.userservice.repo.UserRepository;
import com.nuclei.userservice.util.EmailEncryptionUtil;
import com.nuclei.userservice.util.EmailUtil;
import com.nuclei.userservice.util.JwtService;
import com.nuclei.userservice.util.PasswordUtil;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordUtil passwordUtil;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailUtil emailUtil;

    @Mock
    private EmailEncryptionUtil emailEncryptionUtil;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    @Test
    void authenticate_shouldReturnJwtForValidCredentials() {
        final String email = " TEST@EMAIL.COM ";
        final String normalizedEmail = "test@email.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "password123";
        final String passwordHash = "hashed-password";
        final String token = "jwt-token";

        final User user = new User();
        user.setId(1L);
        user.setPasswordHash(passwordHash);

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(userRepository.findByEmail(encryptedEmail))
                .thenReturn(Optional.of(user));

        when(passwordUtil.matches(password, passwordHash))
                .thenReturn(true);

        when(jwtService.generateToken(1L))
                .thenReturn(token);

        final String result =
                authenticationService.authenticate(email, password);

        assertEquals(token, result);

        verify(emailUtil).normalize(email);
        verify(emailEncryptionUtil).encrypt(normalizedEmail);
        verify(userRepository).findByEmail(encryptedEmail);
        verify(passwordUtil).matches(password, passwordHash);
        verify(jwtService).generateToken(1L);
    }

    @Test
    void authenticate_shouldThrowExceptionWhenUserDoesNotExist() {
        final String email = "test@email.com";
        final String normalizedEmail = "test@email.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "password123";

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(userRepository.findByEmail(encryptedEmail))
                .thenReturn(Optional.empty());

        assertThrows(
                AuthenticationException.class,
                () -> authenticationService.authenticate(
                        email,
                        password
                )
        );

        verify(emailUtil).normalize(email);
        verify(emailEncryptionUtil).encrypt(normalizedEmail);
        verify(userRepository).findByEmail(encryptedEmail);
        verifyNoInteractions(passwordUtil, jwtService);
    }

    @Test
    void authenticate_shouldThrowExceptionWhenPasswordIsInvalid() {
        final String email = "test@email.com";
        final String normalizedEmail = "test@email.com";
        final String encryptedEmail = "encrypted-email";
        final String password = "wrong-password";
        final String passwordHash = "hashed-password";

        final User user = new User();
        user.setId(1L);
        user.setPasswordHash(passwordHash);

        when(emailUtil.normalize(email))
                .thenReturn(normalizedEmail);

        when(emailEncryptionUtil.encrypt(normalizedEmail))
                .thenReturn(encryptedEmail);

        when(userRepository.findByEmail(encryptedEmail))
                .thenReturn(Optional.of(user));

        when(passwordUtil.matches(password, passwordHash))
                .thenReturn(false);

        assertThrows(
                AuthenticationException.class,
                () -> authenticationService.authenticate(
                        email,
                        password
                )
        );

        verify(emailUtil).normalize(email);
        verify(emailEncryptionUtil).encrypt(normalizedEmail);
        verify(userRepository).findByEmail(encryptedEmail);
        verify(passwordUtil).matches(password, passwordHash);
        verifyNoInteractions(jwtService);
    }
}