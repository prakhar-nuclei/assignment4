package util

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"strings"
)

var ErrMissingIdempotencySecret = errors.New(
	"idempotency secret is required",
)

type idempotencyInput struct {
	Name  string `json:"name"`
	Email string `json:"email"`
}

// GenerateIdempotencyKey generates a deterministic key from
// the user's name and normalized email.
// The password is intentionally excluded.
func GenerateIdempotencyKey(
	name string,
	email string,
	secret string,
) (string, error) {
	if strings.TrimSpace(secret) == "" {
		return "", ErrMissingIdempotencySecret
	}

	normalizedEmail := strings.ToLower(strings.TrimSpace(email))

	input, err := json.Marshal(idempotencyInput{
		Name:  name,
		Email: normalizedEmail,
	})
	if err != nil {
		return "", err
	}

	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write(input)

	return hex.EncodeToString(mac.Sum(nil)), nil
}
