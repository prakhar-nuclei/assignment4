package util

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"sort"
	"strings"
)

var (
	ErrInvalidOrderFingerprint = errors.New(
		"order fingerprint requires a positive user ID and valid items",
	)
	ErrMissingOrderIdempotencySecret = errors.New(
		"order idempotency secret is required",
	)
)

type OrderItemFingerprint struct {
	ProductID int64 `json:"product_id"`
	Quantity  int32 `json:"quantity"`
}

type orderIdempotencyInput struct {
	UserID int64                  `json:"user_id"`
	Items  []OrderItemFingerprint `json:"items"`
}

// GenerateOrderIdempotencyKey generates a deterministic key for a user
// and a set of order items. Item order does not affect the generated key.
func GenerateOrderIdempotencyKey(
	userID int64,
	items []OrderItemFingerprint,
	secret string,
) (string, error) {
	if strings.TrimSpace(secret) == "" {
		return "", ErrMissingOrderIdempotencySecret
	}

	if userID <= 0 || len(items) == 0 {
		return "", ErrInvalidOrderFingerprint
	}

	canonicalItems := make([]OrderItemFingerprint, len(items))
	copy(canonicalItems, items)

	for _, item := range canonicalItems {
		if item.ProductID <= 0 || item.Quantity <= 0 {
			return "", ErrInvalidOrderFingerprint
		}
	}

	sort.Slice(canonicalItems, func(i, j int) bool {
		if canonicalItems[i].ProductID == canonicalItems[j].ProductID {
			return canonicalItems[i].Quantity < canonicalItems[j].Quantity
		}

		return canonicalItems[i].ProductID < canonicalItems[j].ProductID
	})

	input, err := json.Marshal(orderIdempotencyInput{
		UserID: userID,
		Items:  canonicalItems,
	})
	if err != nil {
		return "", err
	}

	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write(input)

	return hex.EncodeToString(mac.Sum(nil)), nil
}
