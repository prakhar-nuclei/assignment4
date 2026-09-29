package com.nuclei.userservice.grpc.server;

import com.nuclei.user.proto.AuthenticateUserRequest;
import com.nuclei.user.proto.AuthenticateUserResponse;
import com.nuclei.user.proto.CreateUserRequest;
import com.nuclei.user.proto.GetUserRequest;
import com.nuclei.user.proto.UserResponse;
import com.nuclei.user.proto.UserServiceGrpc;
import com.nuclei.userservice.dto.UserCreateRequestDto;
import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.exception.*;
import com.nuclei.userservice.grpc.interceptor.IdempotencyInterceptor;
import com.nuclei.userservice.grpc.mapper.AuthenticationResponseMapper;
import com.nuclei.userservice.grpc.mapper.UserResponseMapper;
import com.nuclei.userservice.security.GrpcJwtAuthInterceptor;
import com.nuclei.userservice.service.IAuthenticationService;
import com.nuclei.userservice.service.IUserService;
import com.nuclei.userservice.validation.UserRequestValidator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.oauth2.jwt.Jwt;

@GrpcService(
        interceptors = {

                IdempotencyInterceptor.class,
                GrpcJwtAuthInterceptor.class

        }
)
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRequestValidator userRequestValidator;
    private final IAuthenticationService authenticationService;
    private final IUserService userService;
    private final UserResponseMapper userResponseMapper;
    private final AuthenticationResponseMapper authenticationResponseMapper;

    public UserGrpcService(
            final IUserService userService,
            final IAuthenticationService authenticationService,
            final UserRequestValidator userRequestValidator,
            final UserResponseMapper userResponseMapper,
            final AuthenticationResponseMapper authenticationResponseMapper) {

        this.userService = userService;
        this.authenticationService = authenticationService;
        this.userRequestValidator = userRequestValidator;
        this.userResponseMapper = userResponseMapper;
        this.authenticationResponseMapper = authenticationResponseMapper;
    }


    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public void authenticateUser(
            final AuthenticateUserRequest request,
            final StreamObserver<AuthenticateUserResponse> responseObserver) {

        try {
            userRequestValidator.validateAuthenticateUser(
                    request.getEmail(),
                    request.getPassword()
            );
            final String token =
                    authenticationService.authenticate(
                            request.getEmail(),
                            request.getPassword()
                    );

            final AuthenticateUserResponse response =
                    authenticationResponseMapper.toGrpcResponse(token);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (final InvalidUserRequestException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (final AuthenticationException exception) {
            responseObserver.onError(
                    Status.UNAUTHENTICATED
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (final Exception exception) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Internal server error")
                            .asRuntimeException()
            );
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public void createUser(
            final CreateUserRequest request,
            final StreamObserver<UserResponse> responseObserver) {

        try {

            userRequestValidator.validateCreateUser(
                    request.getName(),
                    request.getEmail(),
                    request.getPassword()
            );

            final String idempotencyKey =
                    IdempotencyInterceptor.IDEMPOTENCY_KEY.get();

            final UserCreateRequestDto userRequest =
                    new UserCreateRequestDto(
                            request.getName(),
                            request.getEmail(),
                            request.getPassword()
                    );

            final UserResponseDto createdUser =
                    userService.createUser(
                            userRequest,
                            idempotencyKey
                    );

            final UserResponse response =
                    userResponseMapper.toGrpcResponse(createdUser);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (final InvalidUserRequestException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (final UserAlreadyExistsException
        | IdempotencyKeyConflictException exception) {
        responseObserver.onError(
                Status.ALREADY_EXISTS
                        .withDescription(exception.getMessage())
                        .asRuntimeException()
        );
    } catch (final IdempotencyException exception) {
            responseObserver.onError(
                    Status.ABORTED
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (final RedisLockAcquisitionException exception) {
            responseObserver.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (final Exception exception) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Internal server error")
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void getUser(
            final GetUserRequest request,
            final StreamObserver<UserResponse> responseObserver) {

        try {
            userRequestValidator.validateGetUser(
                    request.getUserId()
            );

            final Jwt jwt =
                    GrpcJwtAuthInterceptor.getAuthenticatedJwt();

            if (jwt == null
                    || !String.valueOf(request.getUserId())
                    .equals(jwt.getSubject())) {

                responseObserver.onError(
                        Status.PERMISSION_DENIED
                                .withDescription(
                                        "User is not authorized to access this user"
                                )
                                .asRuntimeException()
                );
                return;
            }

            final UserResponseDto user =
                    userService.getUser(
                            request.getUserId()
                    );

            final UserResponse response =
                    userResponseMapper.toGrpcResponse(user);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (final InvalidUserRequestException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (final UserNotFoundException exception) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        }
    }
}
