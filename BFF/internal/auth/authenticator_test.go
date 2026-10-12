package auth

import (
	"context"
	"errors"
	"testing"

	user "BFF/internal/generated/user"
)

type mockUserLookup struct {
	response  *user.UserResponse
	err       error
	called    bool
	requestID int64
	token     string
}

func (m *mockUserLookup) GetUser(
	_ context.Context,
	request *user.GetUserRequest,
	token string,
) (*user.UserResponse, error) {
	m.called = true
	m.requestID = request.GetUserId()
	m.token = token

	return m.response, m.err
}

func TestAuthenticate(t *testing.T) {
	validToken := makeTestToken("42")

	tests := []struct {
		name           string
		header         string
		mockResponse   *user.UserResponse
		mockError      error
		wantUserID     int64
		wantError      bool
		wantLookupCall bool
	}{
		{
			name:           "successful authentication",
			header:         "Bearer " + validToken,
			mockResponse:   &user.UserResponse{UserId: 42},
			wantUserID:     42,
			wantLookupCall: true,
		},
		{
			name:           "missing bearer token",
			header:         "",
			wantError:      true,
			wantLookupCall: false,
		},
		{
			name:           "malformed token",
			header:         "Bearer invalid",
			wantError:      true,
			wantLookupCall: false,
		},
		{
			name:           "User Service rejects token",
			header:         "Bearer " + validToken,
			mockError:      errors.New("permission denied"),
			wantError:      true,
			wantLookupCall: true,
		},
		{
			name:           "User Service returns a different user",
			header:         "Bearer " + validToken,
			mockResponse:   &user.UserResponse{UserId: 99},
			wantError:      true,
			wantLookupCall: true,
		},
		{
			name:           "User Service returns empty response",
			header:         "Bearer " + validToken,
			wantError:      true,
			wantLookupCall: true,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			mock := &mockUserLookup{
				response: test.mockResponse,
				err:      test.mockError,
			}
			authenticator := NewAuthenticator(mock)

			response, err := authenticator.Authenticate(
				context.Background(),
				test.header,
			)

			if (err != nil) != test.wantError {
				t.Fatalf("Authenticate() error = %v, wantError %v", err, test.wantError)
			}

			if mock.called != test.wantLookupCall {
				t.Fatalf("User Service called = %v, want %v", mock.called, test.wantLookupCall)
			}

			if !test.wantError {
				if response == nil {
					t.Fatal("expected a user response")
				}
				if response.GetUserId() != test.wantUserID {
					t.Errorf("user ID = %d, want %d", response.GetUserId(), test.wantUserID)
				}
			}

			if mock.called && mock.requestID != 42 {
				t.Errorf("requested user ID = %d, want 42", mock.requestID)
			}
		})
	}
}
