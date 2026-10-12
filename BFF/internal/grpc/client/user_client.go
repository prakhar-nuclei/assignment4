package client

import (
	"context"

	user "BFF/internal/generated/user"

	"google.golang.org/grpc/metadata"
)

type UserClient struct {
	client user.UserServiceClient
}

func NewUserClient(client user.UserServiceClient) *UserClient {
	return &UserClient{
		client: client,
	}
}

func (c *UserClient) CreateUser(
	ctx context.Context,
	request *user.CreateUserRequest,
	idempotencyKey string,
) (*user.UserResponse, error) {
	ctx = metadata.AppendToOutgoingContext(
		ctx,
		"idempotency-key",
		idempotencyKey,
	)

	return c.client.CreateUser(ctx, request)
}

func (c *UserClient) GetUser(
	ctx context.Context,
	request *user.GetUserRequest,
	token string,
) (*user.UserResponse, error) {
	ctx = metadata.AppendToOutgoingContext(
		ctx,
		"authorization",
		"Bearer "+token,
	)

	return c.client.GetUser(ctx, request)
}

func (c *UserClient) AuthenticateUser(
	ctx context.Context,
	request *user.AuthenticateUserRequest,
) (*user.AuthenticateUserResponse, error) {
	return c.client.AuthenticateUser(ctx, request)
}
