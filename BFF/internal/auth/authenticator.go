package auth

import (
	"context"
	"errors"
	"fmt"
	"strings"

	user "BFF/internal/generated/user"
)

var (
	ErrMissingBearerToken = errors.New("missing or invalid bearer token")
	ErrUserIDMismatch     = errors.New("authenticated user ID mismatch")
	ErrEmptyUserResponse  = errors.New("empty user response")
)

// UserLookup defines the User Service operation required for authentication
type UserLookup interface {
	GetUser(
		ctx context.Context,
		request *user.GetUserRequest,
		token string,
	) (*user.UserResponse, error)
}

type Authenticator struct {
	userClient UserLookup
}

func NewAuthenticator(userClient UserLookup) *Authenticator {
	return &Authenticator{
		userClient: userClient,
	}
}

// Authenticate verifies the candidate identity through the User Service.
func (a *Authenticator) Authenticate(
	ctx context.Context,
	authorizationHeader string,
) (*user.UserResponse, error) {
	token, err := extractBearerToken(authorizationHeader)
	if err != nil {
		return nil, err
	}

	candidateUserID, err := CandidateUserID(token)
	if err != nil {
		return nil, err
	}

	userResponse, err := a.userClient.GetUser(
		ctx,
		&user.GetUserRequest{
			UserId: candidateUserID,
		},
		token,
	)
	if err != nil {
		return nil, fmt.Errorf("User Service authentication failed: %w", err)
	}

	if userResponse == nil {
		return nil, ErrEmptyUserResponse
	}

	if userResponse.GetUserId() != candidateUserID {
		return nil, ErrUserIDMismatch
	}

	return userResponse, nil
}

func extractBearerToken(authorizationHeader string) (string, error) {
	parts := strings.Fields(authorizationHeader)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
		return "", ErrMissingBearerToken
	}

	return parts[1], nil
}
