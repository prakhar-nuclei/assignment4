package com.nuclei.userservice.grpc.server;

import com.nuclei.user.proto.AuthenticateUserRequest;
import com.nuclei.user.proto.AuthenticateUserResponse;
import com.nuclei.user.proto.CreateUserRequest;
import com.nuclei.user.proto.GetUserRequest;
import com.nuclei.user.proto.UserResponse;
import com.nuclei.user.proto.UserServiceGrpc;
import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.exception.AuthenticationException;
import com.nuclei.userservice.exception.InvalidUserRequestException;
import com.nuclei.userservice.exception.UserAlreadyExistsException;
import com.nuclei.userservice.exception.UserNotFoundException;
import com.nuclei.userservice.service.IAuthenticationService;
import com.nuclei.userservice.service.IUserService;
import com.nuclei.userservice.validation.UserRequestValidator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase{

    private final UserRequestValidator userRequestValidator;
    private final IAuthenticationService authenticationService;
    private final IUserService userService;

    public UserGrpcService(
            final IUserService userService,
            final IAuthenticationService authenticationService,
            final UserRequestValidator userRequestValidator) {

        this.userService = userService;
        this.authenticationService = authenticationService;
        this.userRequestValidator = userRequestValidator;
    }


    @Override
    public void authenticateUser(
            final AuthenticateUserRequest request,
            final StreamObserver<AuthenticateUserResponse> responseObserver) {

        try {
            userRequestValidator.validateAuthenticateUser(
                    request.getEmail(),
                    request.getPassword()
            );
            final String token = authenticationService.authenticate(
                    request.getEmail(),
                    request.getPassword()
            );

            final AuthenticateUserResponse response =
                    AuthenticateUserResponse.newBuilder()
                            .setAccessToken(token)
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (final InvalidUserRequestException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        }

        catch (final AuthenticationException exception) {
            responseObserver.onError(
                    Status.UNAUTHENTICATED
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        }
    }

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

            final UserResponseDto createdUser = userService.createUser(
                    request.getName(),
                    request.getEmail(),
                    request.getPassword()
            );

            final UserResponse response = UserResponse.newBuilder()
                    .setUserId(createdUser.userId())
                    .setName(createdUser.name())
                    .setEmail(createdUser.email())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        }catch (final InvalidUserRequestException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        }
        catch (final UserAlreadyExistsException exception) {

            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription(exception.getMessage())
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
            final UserResponseDto user = userService.getUser(
                    request.getUserId()
            );

            final UserResponse response = UserResponse.newBuilder()
                    .setUserId(user.userId())
                    .setName(user.name())
                    .setEmail(user.email())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (final InvalidUserRequestException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        }
        catch (final UserNotFoundException exception) {

            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        }
    }
}
