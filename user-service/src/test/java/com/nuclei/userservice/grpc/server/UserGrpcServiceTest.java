package com.nuclei.userservice.grpc.server;

import com.nuclei.user.proto.*;
import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.exception.*;
import com.nuclei.userservice.grpc.mapper.AuthenticationResponseMapper;
import com.nuclei.userservice.grpc.mapper.UserResponseMapper;
import com.nuclei.userservice.security.GrpcJwtAuthInterceptor;
import com.nuclei.userservice.service.IAuthenticationService;
import com.nuclei.userservice.service.IUserService;
import com.nuclei.userservice.validation.UserRequestValidator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.jwt.Jwt;

class UserGrpcServiceTest {

    @Mock
    private IUserService userService;

    @Mock
    private IAuthenticationService authenticationService;

    @Mock
    private UserRequestValidator userRequestValidator;

    @Mock
    private UserResponseMapper userResponseMapper;

    @Mock
    private AuthenticationResponseMapper authenticationResponseMapper;

    @Mock
    private StreamObserver<UserResponse> responseObserver;

    @Mock
    private StreamObserver<AuthenticateUserResponse> authenticateResponseObserver;

    private UserGrpcService userGrpcService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        userGrpcService =
                new UserGrpcService(
                        userService,
                        authenticationService,
                        userRequestValidator,
                        userResponseMapper,
                        authenticationResponseMapper
                );
    }

    @Test
    void createUser_shouldReturnMappedUserResponse() {
        final CreateUserRequest request =
                CreateUserRequest.newBuilder()
                        .setName("Prakhar")
                        .setEmail("prakhar@example.com")
                        .setPassword("password123")
                        .build();

        final UserResponseDto userResponseDto =
                new UserResponseDto(
                        1L,
                        "Prakhar",
                        "prakhar@example.com"
                );

        final UserResponse grpcResponse =
                UserResponse.newBuilder()
                        .setUserId(1L)
                        .setName("Prakhar")
                        .setEmail("prakhar@example.com")
                        .build();

        when(userService.createUser(
                "Prakhar",
                "prakhar@example.com",
                "password123",
                null
        )).thenReturn(userResponseDto);

        when(userResponseMapper.toGrpcResponse(userResponseDto))
                .thenReturn(grpcResponse);

        userGrpcService.createUser(
                request,
                responseObserver
        );

        verify(userRequestValidator).validateCreateUser(
                "Prakhar",
                "prakhar@example.com",
                "password123"
        );

        verify(userService).createUser(
                "Prakhar",
                "prakhar@example.com",
                "password123",
                null
        );

        verify(userResponseMapper).toGrpcResponse(
                userResponseDto
        );

        verify(responseObserver).onNext(grpcResponse);
        verify(responseObserver).onCompleted();

        assertEquals(
                1L,
                grpcResponse.getUserId()
        );
        assertEquals(
                "Prakhar",
                grpcResponse.getName()
        );
        assertEquals(
                "prakhar@example.com",
                grpcResponse.getEmail()
        );
    }

    @Test
    void createUser_shouldReturnInvalidArgumentForInvalidRequest() {
        final CreateUserRequest request =
                CreateUserRequest.newBuilder()
                        .setName("")
                        .setEmail("invalid-email")
                        .setPassword("")
                        .build();

        doThrow(new InvalidUserRequestException("Invalid user request"))
                .when(userRequestValidator)
                .validateCreateUser(
                        "",
                        "invalid-email",
                        ""
                );

        userGrpcService.createUser(
                request,
                responseObserver
        );

        verify(responseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.INVALID_ARGUMENT
                                && "Invalid user request"
                                .equals(Status.fromThrowable(throwable).getDescription())
                )
        );

        verifyNoInteractions(userService);
        verifyNoInteractions(userResponseMapper);
    }

    @Test
    void createUser_shouldReturnAlreadyExistsForDuplicateUser() {
        final CreateUserRequest request =
                CreateUserRequest.newBuilder()
                        .setName("Prakhar")
                        .setEmail("prakhar@example.com")
                        .setPassword("password123")
                        .build();

        when(userService.createUser(
                "Prakhar",
                "prakhar@example.com",
                "password123",
                null
        )).thenThrow(
                new UserAlreadyExistsException("User already exists")
        );

        userGrpcService.createUser(
                request,
                responseObserver
        );

        verify(responseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.ALREADY_EXISTS
                                && "User already exists"
                                .equals(
                                        Status.fromThrowable(throwable)
                                                .getDescription()
                                )
                )
        );

        verify(userRequestValidator).validateCreateUser(
                "Prakhar",
                "prakhar@example.com",
                "password123"
        );

        verifyNoInteractions(userResponseMapper);
    }

    @Test
    void createUser_shouldReturnAbortedForIdempotencyFailure() {
        final CreateUserRequest request =
                CreateUserRequest.newBuilder()
                        .setName("Prakhar")
                        .setEmail("prakhar@example.com")
                        .setPassword("password123")
                        .build();

        when(userService.createUser(
                "Prakhar",
                "prakhar@example.com",
                "password123",
                null
        )).thenThrow(
                new IdempotencyException("Idempotency operation failed")
        );

        userGrpcService.createUser(
                request,
                responseObserver
        );

        verify(responseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.ABORTED
                                && "Idempotency operation failed"
                                .equals(
                                        Status.fromThrowable(throwable)
                                                .getDescription()
                                )
                )
        );

        verify(userRequestValidator).validateCreateUser(
                "Prakhar",
                "prakhar@example.com",
                "password123"
        );

        verifyNoInteractions(userResponseMapper);
    }

    @Test
    void createUser_shouldReturnResourceExhaustedForRedisLockFailure() {
        final CreateUserRequest request =
                CreateUserRequest.newBuilder()
                        .setName("Prakhar")
                        .setEmail("prakhar@example.com")
                        .setPassword("password123")
                        .build();

        when(userService.createUser(
                "Prakhar",
                "prakhar@example.com",
                "password123",
                null
        )).thenThrow(
                new RedisLockAcquisitionException(
                        "Failed to acquire Redis lock"
                )
        );

        userGrpcService.createUser(
                request,
                responseObserver
        );

        verify(responseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.RESOURCE_EXHAUSTED
                                && "Failed to acquire Redis lock"
                                .equals(
                                        Status.fromThrowable(throwable)
                                                .getDescription()
                                )
                )
        );

        verify(userRequestValidator).validateCreateUser(
                "Prakhar",
                "prakhar@example.com",
                "password123"
        );

        verifyNoInteractions(userResponseMapper);
    }


    @Test
    void authenticateUser_shouldReturnAccessToken() {
        final AuthenticateUserRequest request =
                AuthenticateUserRequest.newBuilder()
                        .setEmail("prakhar@example.com")
                        .setPassword("password123")
                        .build();

        final String token = "jwt-token";

        final AuthenticateUserResponse response =
                AuthenticateUserResponse.newBuilder()
                        .setAccessToken(token)
                        .build();

        when(authenticationService.authenticate(
                "prakhar@example.com",
                "password123"
        )).thenReturn(token);

        when(authenticationResponseMapper.toGrpcResponse(token))
                .thenReturn(response);

        userGrpcService.authenticateUser(
                request,
                authenticateResponseObserver
        );

        verify(userRequestValidator).validateAuthenticateUser(
                "prakhar@example.com",
                "password123"
        );

        verify(authenticationService).authenticate(
                "prakhar@example.com",
                "password123"
        );

        verify(authenticationResponseMapper).toGrpcResponse(token);

        verify(authenticateResponseObserver).onNext(response);
        verify(authenticateResponseObserver).onCompleted();
    }

    @Test
    void authenticateUser_shouldReturnInvalidArgumentForInvalidRequest() {
        final AuthenticateUserRequest request =
                AuthenticateUserRequest.newBuilder()
                        .setEmail("invalid-email")
                        .setPassword("")
                        .build();

        doThrow(new InvalidUserRequestException("Invalid authentication request"))
                .when(userRequestValidator)
                .validateAuthenticateUser(
                        "invalid-email",
                        ""
                );

        userGrpcService.authenticateUser(
                request,
                authenticateResponseObserver
        );

        verify(authenticateResponseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.INVALID_ARGUMENT
                                && "Invalid authentication request".equals(
                                Status.fromThrowable(throwable)
                                        .getDescription()
                        )
                )
        );

        verifyNoInteractions(authenticationService);
        verifyNoInteractions(authenticationResponseMapper);
    }

    @Test
    void authenticateUser_shouldReturnUnauthenticatedForAuthenticationFailure() {
        final AuthenticateUserRequest request =
                AuthenticateUserRequest.newBuilder()
                        .setEmail("prakhar@example.com")
                        .setPassword("wrongpassword")
                        .build();

        when(authenticationService.authenticate(
                "prakhar@example.com",
                "wrongpassword"
        )).thenThrow(
                new AuthenticationException("Invalid credentials")
        );

        userGrpcService.authenticateUser(
                request,
                authenticateResponseObserver
        );

        verify(authenticateResponseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.UNAUTHENTICATED
                                && "Invalid credentials".equals(
                                Status.fromThrowable(throwable)
                                        .getDescription()
                        )
                )
        );

        verify(userRequestValidator).validateAuthenticateUser(
                "prakhar@example.com",
                "wrongpassword"
        );

        verifyNoInteractions(authenticationResponseMapper);
    }

    @Test
    void getUser_shouldReturnMappedUserResponse() {
        final GetUserRequest request =
                GetUserRequest.newBuilder()
                        .setUserId(1L)
                        .build();

        final Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "RS256")
                .claim("sub", "1")
                .claim("iss", "user-service")
                .build();

        final UserResponseDto userResponseDto =
                new UserResponseDto(
                        1L,
                        "Prakhar",
                        "prakhar@example.com"
                );

        final UserResponse grpcResponse =
                UserResponse.newBuilder()
                        .setUserId(1L)
                        .setName("Prakhar")
                        .setEmail("prakhar@example.com")
                        .build();

        when(userService.getUser(1L))
                .thenReturn(userResponseDto);

        when(userResponseMapper.toGrpcResponse(userResponseDto))
                .thenReturn(grpcResponse);

        try (MockedStatic<GrpcJwtAuthInterceptor> mockedInterceptor =
                     mockStatic(GrpcJwtAuthInterceptor.class)) {

            mockedInterceptor
                    .when(GrpcJwtAuthInterceptor::getAuthenticatedJwt)
                    .thenReturn(jwt);

            userGrpcService.getUser(
                    request,
                    responseObserver
            );
        }

        verify(userRequestValidator).validateGetUser(1L);

        verify(userService).getUser(1L);

        verify(userResponseMapper).toGrpcResponse(
                userResponseDto
        );

        verify(responseObserver).onNext(grpcResponse);
        verify(responseObserver).onCompleted();
    }

    @Test
    void getUser_shouldReturnPermissionDeniedForDifferentUser() {
        final GetUserRequest request =
                GetUserRequest.newBuilder()
                        .setUserId(1L)
                        .build();

        final Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "RS256")
                .claim("sub", "2")
                .claim("iss", "user-service")
                .build();

        try (MockedStatic<GrpcJwtAuthInterceptor> mockedInterceptor =
                     mockStatic(GrpcJwtAuthInterceptor.class)) {

            mockedInterceptor
                    .when(GrpcJwtAuthInterceptor::getAuthenticatedJwt)
                    .thenReturn(jwt);

            userGrpcService.getUser(
                    request,
                    responseObserver
            );
        }

        verify(responseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.PERMISSION_DENIED
                                && "User is not authorized to access this user"
                                .equals(
                                        Status.fromThrowable(throwable)
                                                .getDescription()
                                )
                )
        );

        verify(userRequestValidator).validateGetUser(1L);

        verifyNoInteractions(userService);
        verifyNoInteractions(userResponseMapper);
    }

    @Test
    void getUser_shouldReturnNotFoundWhenUserDoesNotExist() {
        final GetUserRequest request =
                GetUserRequest.newBuilder()
                        .setUserId(1L)
                        .build();

        final Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "RS256")
                .claim("sub", "1")
                .claim("iss", "user-service")
                .build();

        when(userService.getUser(1L))
                .thenThrow(
                        new UserNotFoundException("User not found")
                );

        try (MockedStatic<GrpcJwtAuthInterceptor> mockedInterceptor =
                     mockStatic(GrpcJwtAuthInterceptor.class)) {

            mockedInterceptor
                    .when(GrpcJwtAuthInterceptor::getAuthenticatedJwt)
                    .thenReturn(jwt);

            userGrpcService.getUser(
                    request,
                    responseObserver
            );
        }

        verify(responseObserver).onError(
                argThat(throwable ->
                        Status.fromThrowable(throwable).getCode()
                                == Status.Code.NOT_FOUND
                                && "User not found".equals(
                                Status.fromThrowable(throwable)
                                        .getDescription()
                        )
                )
        );

        verify(userRequestValidator).validateGetUser(1L);

        verify(userService).getUser(1L);

        verifyNoInteractions(userResponseMapper);
    }
}