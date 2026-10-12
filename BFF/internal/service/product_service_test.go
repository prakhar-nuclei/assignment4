package service

import (
	"context"
	"errors"
	"testing"

	request "BFF/internal/dto/request"
	"BFF/internal/enums"
	product "BFF/internal/generated/product"
	"BFF/internal/util"
)

type mockProductClient struct {
	response *product.ProductResponse
	err      error

	getCalls        int
	lastProductID   int64
	updateCalls     int
	lastUpdateID    int64
	lastQuantity    int32
	lastOperationID string
	lastDirection   product.StockOperationDirection
}

func (m *mockProductClient) GetProduct(
	_ context.Context,
	productID int64,
) (*product.ProductResponse, error) {
	m.getCalls++
	m.lastProductID = productID

	return m.response, m.err
}

func (m *mockProductClient) UpdateStock(
	_ context.Context,
	productID int64,
	quantity int32,
	operationID string,
	direction product.StockOperationDirection,
) (*product.ProductResponse, error) {
	m.updateCalls++
	m.lastUpdateID = productID
	m.lastQuantity = quantity
	m.lastOperationID = operationID
	m.lastDirection = direction

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
		t.Errorf("expected product ID 10 in request, got %d", client.lastProductID)
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

func TestUpdateStockSuccess(t *testing.T) {
	client := &mockProductClient{
		response: &product.ProductResponse{
			ProductId: 10,
			Name:      "Keyboard",
			Price:     "123.40",
			Stock:     15,
		},
	}
	svc := NewProductService(client)

	const userID int64 = 7
	const productID int64 = 10
	const idempotencyKey = "stock-request-123"

	result, err := svc.UpdateStock(
		context.Background(),
		userID,
		productID,
		idempotencyKey,
		&request.UpdateStockRequest{
			Quantity:  5,
			Direction: enums.StockOperationDirectionDecrease,
		},
	)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if result == nil {
		t.Fatal("expected a response")
	}
	if result.GetStock() != 15 {
		t.Errorf("expected stock 15, got %d", result.GetStock())
	}
	if client.updateCalls != 1 {
		t.Fatalf("expected 1 client call, got %d", client.updateCalls)
	}
	if client.lastUpdateID != productID {
		t.Errorf("expected product ID %d, got %d", productID, client.lastUpdateID)
	}
	if client.lastQuantity != 5 {
		t.Errorf("expected quantity 5, got %d", client.lastQuantity)
	}
	if client.lastDirection != product.StockOperationDirection_DECREASE {
		t.Errorf("unexpected direction: %v", client.lastDirection)
	}

	expectedOperationID := util.GenerateStockOperationID(
		userID,
		idempotencyKey,
		0,
	)
	if client.lastOperationID != expectedOperationID {
		t.Errorf(
			"unexpected operation ID: expected %q, got %q",
			expectedOperationID,
			client.lastOperationID,
		)
	}
}

func TestUpdateStockRejectsInvalidUserOrProductID(t *testing.T) {
	tests := []struct {
		name      string
		userID    int64
		productID int64
	}{
		{name: "invalid user ID", userID: 0, productID: 10},
		{name: "invalid product ID", userID: 7, productID: 0},
		{name: "negative user ID", userID: -1, productID: 10},
		{name: "negative product ID", userID: 7, productID: -1},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			client := &mockProductClient{}
			svc := NewProductService(client)

			_, err := svc.UpdateStock(
				context.Background(),
				tt.userID,
				tt.productID,
				"key-123",
				&request.UpdateStockRequest{
					Quantity:  1,
					Direction: enums.StockOperationDirectionIncrease,
				},
			)
			if !errors.Is(err, ErrInvalidProductID) {
				t.Fatalf("expected ErrInvalidProductID, got %v", err)
			}
			if client.updateCalls != 0 {
				t.Errorf("expected no client calls, got %d", client.updateCalls)
			}
		})
	}
}

func TestUpdateStockRejectsMissingIdempotencyKey(t *testing.T) {
	client := &mockProductClient{}
	svc := NewProductService(client)

	_, err := svc.UpdateStock(
		context.Background(),
		7,
		10,
		"   ",
		&request.UpdateStockRequest{
			Quantity:  1,
			Direction: enums.StockOperationDirectionIncrease,
		},
	)
	if !errors.Is(err, ErrMissingIdempotencyKey) {
		t.Fatalf("expected ErrMissingIdempotencyKey, got %v", err)
	}
	if client.updateCalls != 0 {
		t.Errorf("expected no client calls, got %d", client.updateCalls)
	}
}

func TestUpdateStockRejectsInvalidRequest(t *testing.T) {
	tests := []struct {
		name string
		req  *request.UpdateStockRequest
	}{
		{name: "nil request", req: nil},
		{
			name: "zero quantity",
			req: &request.UpdateStockRequest{
				Quantity:  0,
				Direction: enums.StockOperationDirectionIncrease,
			},
		},
		{
			name: "negative quantity",
			req: &request.UpdateStockRequest{
				Quantity:  -1,
				Direction: enums.StockOperationDirectionIncrease,
			},
		},
		{
			name: "unspecified direction",
			req: &request.UpdateStockRequest{
				Quantity: 1,
			},
		},
		{
			name: "unsupported direction",
			req: &request.UpdateStockRequest{
				Quantity:  1,
				Direction: enums.StockOperationDirection("INVALID"),
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			client := &mockProductClient{}
			svc := NewProductService(client)

			_, err := svc.UpdateStock(
				context.Background(),
				7,
				10,
				"key-123",
				tt.req,
			)
			if !errors.Is(err, ErrInvalidStockRequest) {
				t.Fatalf("expected ErrInvalidStockRequest, got %v", err)
			}
			if client.updateCalls != 0 {
				t.Errorf("expected no client calls, got %d", client.updateCalls)
			}
		})
	}
}

func TestUpdateStockSameInputProducesSameOperationID(t *testing.T) {
	client := &mockProductClient{
		response: &product.ProductResponse{
			ProductId: 10,
			Name:      "Keyboard",
			Price:     "123.40",
			Stock:     20,
		},
	}
	svc := NewProductService(client)

	req := &request.UpdateStockRequest{
		Quantity:  2,
		Direction: enums.StockOperationDirectionIncrease,
	}

	_, err := svc.UpdateStock(context.Background(), 7, 10, "key-123", req)
	if err != nil {
		t.Fatalf("first call failed: %v", err)
	}
	firstOperationID := client.lastOperationID

	_, err = svc.UpdateStock(context.Background(), 7, 10, "key-123", req)
	if err != nil {
		t.Fatalf("second call failed: %v", err)
	}

	if firstOperationID == "" {
		t.Fatal("expected a generated operation ID")
	}
	if client.lastOperationID != firstOperationID {
		t.Errorf(
			"expected the same operation ID, got %q and %q",
			firstOperationID,
			client.lastOperationID,
		)
	}
	if client.updateCalls != 2 {
		t.Errorf("expected 2 client calls, got %d", client.updateCalls)
	}
}

func TestUpdateStockPropagatesClientError(t *testing.T) {
	expectedErr := errors.New("Product Service unavailable")
	client := &mockProductClient{err: expectedErr}
	svc := NewProductService(client)

	_, err := svc.UpdateStock(
		context.Background(),
		7,
		10,
		"key-123",
		&request.UpdateStockRequest{
			Quantity:  1,
			Direction: enums.StockOperationDirectionIncrease,
		},
	)
	if !errors.Is(err, expectedErr) {
		t.Fatalf("expected client error, got %v", err)
	}
	if client.updateCalls != 1 {
		t.Errorf("expected 1 client call, got %d", client.updateCalls)
	}
}

func TestUpdateStockRejectsEmptyResponse(t *testing.T) {
	client := &mockProductClient{}
	svc := NewProductService(client)

	_, err := svc.UpdateStock(
		context.Background(),
		7,
		10,
		"key-123",
		&request.UpdateStockRequest{
			Quantity:  1,
			Direction: enums.StockOperationDirectionIncrease,
		},
	)
	if !errors.Is(err, ErrEmptyProductResponse) {
		t.Fatalf("expected ErrEmptyProductResponse, got %v", err)
	}
}
