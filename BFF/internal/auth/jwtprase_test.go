package auth

import (
	"encoding/base64"
	"encoding/json"
	"strconv"
	"strings"
	"testing"
)

func makeTestToken(subject string) string {
	header, _ := json.Marshal(map[string]string{
		"alg": "RS256",
		"typ": "JWT",
	})

	payload, _ := json.Marshal(map[string]string{
		"sub": subject,
	})

	encode := base64.RawURLEncoding.EncodeToString

	return encode(header) + "." +
		encode(payload) + "." +
		encode([]byte("test-signature"))
}

func TestCandidateUserID(t *testing.T) {
	tests := []struct {
		name      string
		token     string
		wantID    int64
		wantError bool
	}{
		{
			name:   "positive numeric subject",
			token:  makeTestToken("123"),
			wantID: 123,
		},
		{
			name:      "malformed token",
			token:     "not-a-jwt",
			wantError: true,
		},
		{
			name:      "missing subject",
			token:     makeTestToken(""),
			wantError: true,
		},
		{
			name:      "non-numeric subject",
			token:     makeTestToken("abc"),
			wantError: true,
		},
		{
			name:      "zero subject",
			token:     makeTestToken("0"),
			wantError: true,
		},
		{
			name:      "negative subject",
			token:     makeTestToken("-5"),
			wantError: true,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			gotID, err := CandidateUserID(test.token)

			if test.wantError {
				if err == nil {
					t.Fatal("expected an error")
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if gotID != test.wantID {
				t.Fatalf("got ID %d, want %d", gotID, test.wantID)
			}
		})
	}
}

func TestCandidateUserIDRejectsInvalidEncoding(t *testing.T) {
	token := "eyJhbGciOiJSUzI1NiJ9.%%%.c2ln"

	_, err := CandidateUserID(token)
	if err == nil {
		t.Fatal("expected an error for invalid payload encoding")
	}
}

func TestCandidateUserIDRejectsWrongAlgorithm(t *testing.T) {
	header := base64.RawURLEncoding.EncodeToString(
		[]byte(`{"alg":"HS256"}`),
	)
	payload := base64.RawURLEncoding.EncodeToString(
		[]byte(`{"sub":"123"}`),
	)

	token := strings.Join([]string{
		header,
		payload,
		base64.RawURLEncoding.EncodeToString(
			[]byte(strconv.Itoa(1)),
		),
	}, ".")

	_, err := CandidateUserID(token)
	if err == nil {
		t.Fatal("expected an error for unsupported algorithm")
	}
}
