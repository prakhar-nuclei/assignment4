package com.nuclei.userservice.service.impl;

import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.entity.User;
import com.nuclei.userservice.exception.UserAlreadyExistsException;
import com.nuclei.userservice.exception.UserNotFoundException;
import com.nuclei.userservice.repo.UserRepository;
import com.nuclei.userservice.service.IUserService;
import com.nuclei.userservice.util.PasswordUtil;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final PasswordUtil passwordUtil;

    public UserServiceImpl(
            final UserRepository userRepository,
            final PasswordUtil passwordUtil) {

        this.userRepository = userRepository;
        this.passwordUtil = passwordUtil;
    }

    @Override
    public UserResponseDto createUser(
            final String name,
            final String email,
            final String password) {

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(
                    "User already exists with email: " + email
            );
        }

        final String hashedPassword = passwordUtil.hash(password);

        final User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(hashedPassword);

        final User savedUser = userRepository.save(user);

        return new UserResponseDto(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail()
        );
    }

    @Override
    public UserResponseDto getUser(final Long userId) {

        final User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        return new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
