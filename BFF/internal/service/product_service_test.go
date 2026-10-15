package service

import (
	"context"
	"errors"
	"testing"

	product "BFF/internal/generated/product"
)

type mockProductClient struct {
	response *product.ProductResponse
	err      error

	getCalls      int
	lastProductID int64
}

func (m *mockProductClient) GetProduct(
	_ context.Context,
	productID int64,
) (*product.ProductResponse, error) {
	m.getCalls++
	m.lastProductID = productID

	return m.response, m.err
}

func TestGetProductSuccess(t *testing.T) {
	client := &mockProductClient{
		response: &product.ProductResponse{
			ProductId: 10,
			Name:      "Keyboard",
			Price:     "123.40",
			Stock:     20,
		},
	}
	svc := NewProductService(client)

	result, err := svc.GetProduct(context.Background(), 10)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result == nil {
		t.Fatal("expected a response")
	}

	if result.GetProductId() != 10 {
		t.Errorf("expected product ID 10, got %d", result.GetProductId())
	}

	if result.GetPrice() != "123.40" {
		t.Errorf("expected price 123.40, got %q", result.GetPrice())
	}

	if client.getCalls != 1 {
		t.Errorf("expected 1 client call, got %d", client.getCalls)
	}

	if client.lastProductID != 10 {
		t.Errorf(
			"expected product ID 10 in request, got %d",
			client.lastProductID,
		)
	}
}

func TestGetProductRejectsInvalidID(t *testing.T) {
	client := &mockProductClient{}
	svc := NewProductService(client)

	_, err := svc.GetProduct(context.Background(), 0)
	if !errors.Is(err, ErrInvalidProductID) {
		t.Fatalf("expected ErrInvalidProductID, got %v", err)
	}

	if client.getCalls != 0 {
		t.Errorf("expected no client calls, got %d", client.getCalls)
	}
}

func TestGetProductPropagatesClientError(t *testing.T) {
	expectedErr := errors.New("Product Service unavailable")
	client := &mockProductClient{err: expectedErr}
	svc := NewProductService(client)

	_, err := svc.GetProduct(context.Background(), 10)
	if !errors.Is(err, expectedErr) {
		t.Fatalf("expected client error, got %v", err)
	}

	if client.getCalls != 1 {
		t.Errorf("expected 1 client call, got %d", client.getCalls)
	}
}

func TestGetProductRejectsEmptyResponse(t *testing.T) {
	client := &mockProductClient{}
	svc := NewProductService(client)

	_, err := svc.GetProduct(context.Background(), 10)
	if !errors.Is(err, ErrEmptyProductResponse) {
		t.Fatalf("expected ErrEmptyProductResponse, got %v", err)
	}
}
