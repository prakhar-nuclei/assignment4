package service

import (
	"context"
	"errors"
	"fmt"

	request "BFF/internal/dto/request"
	response "BFF/internal/dto/response"
	user "BFF/internal/generated/user"
	"BFF/internal/mapper"
	"BFF/internal/util"
)

var ErrEmptyUserResponse = errors.New("User Service returned an empty response")

type UserClient interface {
	CreateUser(
		ctx context.Context,
		request *user.CreateUserRequest,
		idempotencyKey string,
	) (*user.UserResponse, error)

	AuthenticateUser(
		ctx context.Context,
		request *user.AuthenticateUserRequest,
	) (*user.AuthenticateUserResponse, error)
}

type UserService struct {
	userClient        UserClient
	idempotencySecret string
}

func NewUserService(
	userClient UserClient,
	idempotencySecret string,
) *UserService {
	return &UserService{
		userClient:        userClient,
		idempotencySecret: idempotencySecret,
	}
}

func (s *UserService) CreateUser(
	ctx context.Context,
	dto *request.CreateUserRequest,
) (*response.UserResponse, error) {
	if dto == nil {
		return nil, errors.New("create user request is required")
	}

	idempotencyKey, err := util.GenerateIdempotencyKey(
		dto.Name,
		dto.Email,
		s.idempotencySecret,
	)
	if err != nil {
		return nil, fmt.Errorf("generate idempotency key: %w", err)
	}

	grpcRequest := mapper.CreateUserToGRPC(dto)

	grpcResponse, err := s.userClient.CreateUser(
		ctx,
		grpcRequest,
		idempotencyKey,
	)
	if err != nil {
		return nil, fmt.Errorf("create user through User Service: %w", err)
	}

	if grpcResponse == nil {
		return nil, ErrEmptyUserResponse
	}

	return mapper.UserToREST(grpcResponse), nil
}

func (s *UserService) AuthenticateUser(
	ctx context.Context,
	dto *request.AuthenticateUserRequest,
) (*response.AuthenticateUserResponse, error) {
	if dto == nil {
		return nil, errors.New("authenticate user request is required")
	}

	grpcRequest := mapper.AuthenticateUserToGRPC(dto)

	grpcResponse, err := s.userClient.AuthenticateUser(ctx, grpcRequest)
	if err != nil {
		return nil, fmt.Errorf("authenticate user through User Service: %w", err)
	}

	if grpcResponse == nil {
		return nil, ErrEmptyUserResponse
	}

	return &response.AuthenticateUserResponse{
		AccessToken: grpcResponse.GetAccessToken(),
	}, nil
}
