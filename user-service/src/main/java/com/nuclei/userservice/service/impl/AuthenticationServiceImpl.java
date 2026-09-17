package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.entity.User;
import com.nuclei.userservice.exception.AuthenticationException;
import com.nuclei.userservice.repo.UserRepository;
import com.nuclei.userservice.service.IAuthenticationService;
import com.nuclei.userservice.util.JwtService;
import com.nuclei.userservice.util.PasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationServiceImpl implements IAuthenticationService {

    private final UserRepository userRepository;
    private final PasswordUtil passwordUtil;
    private final JwtService jwtService;

    public AuthenticationServiceImpl(
            final UserRepository userRepository,
            final PasswordUtil passwordUtil,
            final JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordUtil = passwordUtil;
        this.jwtService = jwtService;
    }

    @Override
    public String authenticate(final String email, final String password) {

        final User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!passwordUtil.matches(password, user.getPassword())) {
            throw new AuthenticationException("Invalid email or password");
        }

        return jwtService.generateToken(user.getId());
    }
}
