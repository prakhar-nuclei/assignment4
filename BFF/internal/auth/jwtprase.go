package auth

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"strconv"
	"strings"
)

var ErrInvalidToken = errors.New("invalid token")

type jwtClaims struct {
	Subject string `json:"sub"`
}

// CandidateUserID extracts the user ID from the JWT payload.
// It does not verify the JWT signature or establish trust.
func CandidateUserID(token string) (int64, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return 0, ErrInvalidToken
	}

	if parts[0] == "" || parts[1] == "" || parts[2] == "" {
		return 0, ErrInvalidToken
	}

	headerBytes, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return 0, ErrInvalidToken
	}

	var header struct {
		Algorithm string `json:"alg"`
	}

	if err := json.Unmarshal(headerBytes, &header); err != nil {
		return 0, ErrInvalidToken
	}

	if header.Algorithm != "RS256" {
		return 0, ErrInvalidToken
	}

	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return 0, ErrInvalidToken
	}

	var claims jwtClaims
	if err := json.Unmarshal(payloadBytes, &claims); err != nil {
		return 0, ErrInvalidToken
	}

	userID, err := strconv.ParseInt(claims.Subject, 10, 64)
	if err != nil || userID <= 0 {
		return 0, ErrInvalidToken
	}

	return userID, nil
}
