package util

import (
	"errors"
	"testing"
)

func TestGenerateIdempotencyKeyIsDeterministic(t *testing.T) {
	secret := "test-secret-for-idempotency"

	first, err := GenerateIdempotencyKey(
		"Rahul",
		"rahul@example.com",
		secret,
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	second, err := GenerateIdempotencyKey(
		"Rahul",
		"rahul@example.com",
		secret,
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if first != second {
		t.Fatal("same input should generate the same key")
	}
}

func TestGenerateIdempotencyKeyNormalizesEmail(t *testing.T) {
	secret := "test-secret-for-idempotency"

	first, err := GenerateIdempotencyKey(
		"Rahul",
		" Rahul@Example.com ",
		secret,
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	second, err := GenerateIdempotencyKey(
		"Rahul",
		"rahul@example.com",
		secret,
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if first != second {
		t.Fatal("email casing and surrounding whitespace should not change the key")
	}
}

func TestGenerateIdempotencyKeyChangesWhenNameOrEmailChanges(t *testing.T) {
	secret := "test-secret-for-idempotency"

	original, err := GenerateIdempotencyKey(
		"Rahul",
		"rahul@example.com",
		secret,
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	changedName, err := GenerateIdempotencyKey(
		"Rohan",
		"rahul@example.com",
		secret,
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	changedEmail, err := GenerateIdempotencyKey(
		"Rahul",
		"rohan@example.com",
		secret,
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if original == changedName {
		t.Fatal("changing the name should change the key")
	}

	if original == changedEmail {
		t.Fatal("changing the email should change the key")
	}
}

func TestGenerateIdempotencyKeyRejectsMissingSecret(t *testing.T) {
	_, err := GenerateIdempotencyKey(
		"Rahul",
		"rahul@example.com",
		"  ",
	)

	if !errors.Is(err, ErrMissingIdempotencySecret) {
		t.Fatalf("expected ErrMissingIdempotencySecret, got %v", err)
	}
}
