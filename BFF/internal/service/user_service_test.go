package service

import (
	"context"
	"errors"
	"testing"

	request "BFF/internal/dto/request"
	user "BFF/internal/generated/user"
)

type mockUserClient struct {
	response           *user.UserResponse
	err                error
	calls              int
	lastRequest        *user.CreateUserRequest
	lastIdempotencyKey string

	authResponse *user.AuthenticateUserResponse
	authErr      error
	authCalls    int
	authRequest  *user.AuthenticateUserRequest
}

func (m *mockUserClient) CreateUser(
	_ context.Context,
	req *user.CreateUserRequest,
	idempotencyKey string,
) (*user.UserResponse, error) {
	m.calls++
	m.lastRequest = req
	m.lastIdempotencyKey = idempotencyKey

	return m.response, m.err
}

func (m *mockUserClient) AuthenticateUser(
	_ context.Context,
	req *user.AuthenticateUserRequest,
) (*user.AuthenticateUserResponse, error) {
	m.authCalls++
	m.authRequest = req

	return m.authResponse, m.authErr
}

func TestCreateUserSuccess(t *testing.T) {
	client := &mockUserClient{
		response: &user.UserResponse{
			UserId: 1,
			Name:   "Rahul",
			Email:  "rahul@example.com",
		},
	}

	service := NewUserService(client, "test-secret")

	result, err := service.CreateUser(
		context.Background(),
		&request.CreateUserRequest{
			Name:     "Rahul",
			Email:    "rahul@example.com",
			Password: "password123",
		},
	)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result == nil {
		t.Fatal("expected a response")
	}
	if result.UserID != 1 {
		t.Errorf("expected user ID 1, got %d", result.UserID)
	}
	if result.Name != "Rahul" {
		t.Errorf("expected name Rahul, got %q", result.Name)
	}
	if result.Email != "rahul@example.com" {
		t.Errorf("unexpected email: %q", result.Email)
	}
	if client.calls != 1 {
		t.Errorf("expected 1 client call, got %d", client.calls)
	}
	if client.lastIdempotencyKey == "" {
		t.Fatal("expected an idempotency key to be forwarded")
	}
	if client.lastRequest.GetPassword() != "password123" {
		t.Error("expected password to be forwarded in the create request")
	}
}

func TestCreateUserSameInputProducesSameKey(t *testing.T) {
	client := &mockUserClient{
		response: &user.UserResponse{
			UserId: 1,
			Name:   "Rahul",
			Email:  "rahul@example.com",
		},
	}
	service := NewUserService(client, "test-secret")

	firstRequest := &request.CreateUserRequest{
		Name:     "Rahul",
		Email:    " Rahul@Example.com ",
		Password: "password123",
	}
	secondRequest := &request.CreateUserRequest{
		Name:     "Rahul",
		Email:    "rahul@example.com",
		Password: "differentPassword123",
	}

	_, err := service.CreateUser(context.Background(), firstRequest)
	if err != nil {
		t.Fatalf("first call failed: %v", err)
	}
	firstKey := client.lastIdempotencyKey

	_, err = service.CreateUser(context.Background(), secondRequest)
	if err != nil {
		t.Fatalf("second call failed: %v", err)
	}
	secondKey := client.lastIdempotencyKey

	if firstKey != secondKey {
		t.Fatal("same name and normalized email should produce the same key")
	}
	if client.calls != 2 {
		t.Errorf("expected 2 client calls, got %d", client.calls)
	}
}

func TestCreateUserPropagatesClientError(t *testing.T) {
	expectedErr := errors.New("User Service unavailable")
	client := &mockUserClient{err: expectedErr}
	service := NewUserService(client, "test-secret")

	_, err := service.CreateUser(
		context.Background(),
		&request.CreateUserRequest{
			Name:     "Rahul",
			Email:    "rahul@example.com",
			Password: "password123",
		},
	)

	if !errors.Is(err, expectedErr) {
		t.Fatalf("expected wrapped client error, got %v", err)
	}
}

func TestCreateUserRejectsNilRequest(t *testing.T) {
	client := &mockUserClient{}
	service := NewUserService(client, "test-secret")

	_, err := service.CreateUser(context.Background(), nil)

	if err == nil {
		t.Fatal("expected an error for nil request")
	}
	if client.calls != 0 {
		t.Errorf("expected no client calls, got %d", client.calls)
	}
}

func TestCreateUserRejectsMissingSecret(t *testing.T) {
	client := &mockUserClient{}
	service := NewUserService(client, "")

	_, err := service.CreateUser(
		context.Background(),
		&request.CreateUserRequest{
			Name:     "Rahul",
			Email:    "rahul@example.com",
			Password: "password123",
		},
	)

	if err == nil {
		t.Fatal("expected an error when the secret is missing")
	}
	if client.calls != 0 {
		t.Errorf("expected no client calls, got %d", client.calls)
	}
}

func TestCreateUserRejectsEmptyResponse(t *testing.T) {
	client := &mockUserClient{}
	service := NewUserService(client, "test-secret")

	_, err := service.CreateUser(
		context.Background(),
		&request.CreateUserRequest{
			Name:     "Rahul",
			Email:    "rahul@example.com",
			Password: "password123",
		},
	)

	if !errors.Is(err, ErrEmptyUserResponse) {
		t.Fatalf("expected ErrEmptyUserResponse, got %v", err)
	}
}

func TestAuthenticateUserSuccess(t *testing.T) {
	client := &mockUserClient{
		authResponse: &user.AuthenticateUserResponse{
			AccessToken: "test-access-token",
		},
	}
	svc := NewUserService(client, "test-secret")

	result, err := svc.AuthenticateUser(
		context.Background(),
		&request.AuthenticateUserRequest{
			Email:    "rahul@example.com",
			Password: "password123",
		},
	)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if result == nil {
		t.Fatal("expected a response")
	}
	if result.AccessToken != "test-access-token" {
		t.Errorf("unexpected access token: %q", result.AccessToken)
	}
	if client.authCalls != 1 {
		t.Errorf("expected 1 client call, got %d", client.authCalls)
	}
	if client.authRequest.GetEmail() != "rahul@example.com" {
		t.Errorf("unexpected email: %q", client.authRequest.GetEmail())
	}
	if client.authRequest.GetPassword() != "password123" {
		t.Error("expected password to be forwarded")
	}
}

func TestAuthenticateUserPropagatesClientError(t *testing.T) {
	expectedErr := errors.New("User Service unavailable")
	client := &mockUserClient{authErr: expectedErr}
	svc := NewUserService(client, "test-secret")

	_, err := svc.AuthenticateUser(
		context.Background(),
		&request.AuthenticateUserRequest{
			Email:    "rahul@example.com",
			Password: "password123",
		},
	)
	if !errors.Is(err, expectedErr) {
		t.Fatalf("expected wrapped client error, got %v", err)
	}
	if client.authCalls != 1 {
		t.Errorf("expected 1 client call, got %d", client.authCalls)
	}
}

func TestAuthenticateUserRejectsNilRequest(t *testing.T) {
	client := &mockUserClient{}
	svc := NewUserService(client, "test-secret")

	_, err := svc.AuthenticateUser(context.Background(), nil)
	if err == nil {
		t.Fatal("expected an error for nil request")
	}
	if client.authCalls != 0 {
		t.Errorf("expected no client calls, got %d", client.authCalls)
	}
}

func TestAuthenticateUserRejectsEmptyResponse(t *testing.T) {
	client := &mockUserClient{}
	svc := NewUserService(client, "test-secret")

	_, err := svc.AuthenticateUser(
		context.Background(),
		&request.AuthenticateUserRequest{
			Email:    "rahul@example.com",
			Password: "password123",
		},
	)
	if !errors.Is(err, ErrEmptyUserResponse) {
		t.Fatalf("expected ErrEmptyUserResponse, got %v", err)
	}
}
