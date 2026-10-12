package util

import (
	"errors"
	"testing"
)

func TestGenerateOrderIdempotencyKeySameItemsSameKey(t *testing.T) {
	firstItems := []OrderItemFingerprint{
		{ProductID: 2, Quantity: 1},
		{ProductID: 5, Quantity: 3},
	}
	reorderedItems := []OrderItemFingerprint{
		{ProductID: 5, Quantity: 3},
		{ProductID: 2, Quantity: 1},
	}

	firstKey, err := GenerateOrderIdempotencyKey(10, firstItems, "test-secret")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	secondKey, err := GenerateOrderIdempotencyKey(10, reorderedItems, "test-secret")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if firstKey != secondKey {
		t.Fatal("same user and same items should produce the same key")
	}
}

func TestGenerateOrderIdempotencyKeyChangesForDifferentInput(t *testing.T) {
	baseItems := []OrderItemFingerprint{
		{ProductID: 2, Quantity: 1},
		{ProductID: 5, Quantity: 3},
	}

	baseKey, err := GenerateOrderIdempotencyKey(10, baseItems, "test-secret")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	tests := []struct {
		name   string
		userID int64
		items  []OrderItemFingerprint
	}{
		{
			name:   "different user",
			userID: 11,
			items:  baseItems,
		},
		{
			name:   "different quantity",
			userID: 10,
			items: []OrderItemFingerprint{
				{ProductID: 2, Quantity: 2},
				{ProductID: 5, Quantity: 3},
			},
		},
		{
			name:   "different product",
			userID: 10,
			items: []OrderItemFingerprint{
				{ProductID: 2, Quantity: 1},
				{ProductID: 6, Quantity: 3},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			key, err := GenerateOrderIdempotencyKey(
				tt.userID,
				tt.items,
				"test-secret",
			)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}

			if key == baseKey {
				t.Fatal("different input should produce a different key")
			}
		})
	}
}

func TestGenerateOrderIdempotencyKeyRejectsInvalidInput(t *testing.T) {
	tests := []struct {
		name   string
		userID int64
		items  []OrderItemFingerprint
		secret string
		want   error
	}{
		{
			name:   "missing secret",
			userID: 10,
			items:  []OrderItemFingerprint{{ProductID: 1, Quantity: 1}},
			secret: " ",
			want:   ErrMissingOrderIdempotencySecret,
		},
		{
			name:   "invalid user",
			userID: 0,
			items:  []OrderItemFingerprint{{ProductID: 1, Quantity: 1}},
			secret: "test-secret",
			want:   ErrInvalidOrderFingerprint,
		},
		{
			name:   "empty items",
			userID: 10,
			items:  nil,
			secret: "test-secret",
			want:   ErrInvalidOrderFingerprint,
		},
		{
			name:   "invalid product",
			userID: 10,
			items:  []OrderItemFingerprint{{ProductID: 0, Quantity: 1}},
			secret: "test-secret",
			want:   ErrInvalidOrderFingerprint,
		},
		{
			name:   "invalid quantity",
			userID: 10,
			items:  []OrderItemFingerprint{{ProductID: 1, Quantity: 0}},
			secret: "test-secret",
			want:   ErrInvalidOrderFingerprint,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := GenerateOrderIdempotencyKey(
				tt.userID,
				tt.items,
				tt.secret,
			)

			if !errors.Is(err, tt.want) {
				t.Fatalf("expected error %v, got %v", tt.want, err)
			}
		})
	}
}
