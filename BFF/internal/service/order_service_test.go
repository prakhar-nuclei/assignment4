package service

import (
	"context"
	"errors"
	"testing"

	"BFF/internal/dto/request"
	order "BFF/internal/generated/order"
	user "BFF/internal/generated/user"
	"BFF/internal/util"
)

type mockOrderClient struct {
	createOrderFunc func(
		ctx context.Context,
		idempotencyKey string,
		items []*order.OrderItemRequest,
		verifiedUser *user.UserResponse,
	) (*order.OrderResponse, error)

	getOrderFunc func(
		ctx context.Context,
		orderID int64,
		userID int64,
	) (*order.OrderResponse, error)
}

func (m *mockOrderClient) CreateOrder(
	ctx context.Context,
	idempotencyKey string,
	items []*order.OrderItemRequest,
	verifiedUser *user.UserResponse,
) (*order.OrderResponse, error) {
	return m.createOrderFunc(ctx, idempotencyKey, items, verifiedUser)
}

func (m *mockOrderClient) GetOrder(
	ctx context.Context,
	orderID int64,
	userID int64,
) (*order.OrderResponse, error) {
	return m.getOrderFunc(ctx, orderID, userID)
}

func TestOrderServiceCreateOrder(t *testing.T) {
	secret := "test-secret"
	verifiedUser := &user.UserResponse{
		UserId: 10,
		Name:   "Test User",
		Email:  "test@example.com",
	}
	dto := &request.CreateOrderRequest{
		Items: []request.CreateOrderItemRequest{
			{ProductID: 2, Quantity: 1},
			{ProductID: 5, Quantity: 3},
		},
	}
	expectedResponse := &order.OrderResponse{
		OrderId:     101,
		UserId:      10,
		TotalAmount: "125.00",
		Status:      "CREATED",
	}

	mock := &mockOrderClient{
		createOrderFunc: func(
			_ context.Context,
			key string,
			items []*order.OrderItemRequest,
			gotUser *user.UserResponse,
		) (*order.OrderResponse, error) {
			if key == "" {
				t.Fatal("expected a generated idempotency key")
			}
			if gotUser.GetUserId() != verifiedUser.GetUserId() {
				t.Fatal("expected verified user to be forwarded")
			}
			if len(items) != 2 {
				t.Fatalf("expected 2 items, got %d", len(items))
			}
			if items[0].GetProductId() != 2 || items[0].GetQuantity() != 1 {
				t.Fatal("first item was mapped incorrectly")
			}
			if items[1].GetProductId() != 5 || items[1].GetQuantity() != 3 {
				t.Fatal("second item was mapped incorrectly")
			}

			expectedKey, err := util.GenerateOrderIdempotencyKey(
				verifiedUser.GetUserId(),
				[]util.OrderItemFingerprint{
					{ProductID: 2, Quantity: 1},
					{ProductID: 5, Quantity: 3},
				},
				secret,
			)
			if err != nil {
				t.Fatalf("failed to generate expected key: %v", err)
			}
			if key != expectedKey {
				t.Fatal("unexpected idempotency key")
			}

			return expectedResponse, nil
		},
	}

	svc := NewOrderService(mock, secret)
	got, err := svc.CreateOrder(context.Background(), verifiedUser, dto)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != expectedResponse {
		t.Fatal("expected service to return the Order Service response")
	}
}

func TestOrderServiceCreateOrderSameItemsGenerateSameKey(t *testing.T) {
	secret := "test-secret"
	verifiedUser := &user.UserResponse{UserId: 10}

	var firstKey string
	callCount := 0

	mock := &mockOrderClient{
		createOrderFunc: func(
			_ context.Context,
			key string,
			_ []*order.OrderItemRequest,
			_ *user.UserResponse,
		) (*order.OrderResponse, error) {
			callCount++
			if callCount == 1 {
				firstKey = key
			} else if key != firstKey {
				t.Fatal("same user and items should generate the same key")
			}
			return &order.OrderResponse{OrderId: 101}, nil
		},
	}

	svc := NewOrderService(mock, secret)

	requests := []*request.CreateOrderRequest{
		{Items: []request.CreateOrderItemRequest{
			{ProductID: 2, Quantity: 1},
			{ProductID: 5, Quantity: 3},
		}},
		{Items: []request.CreateOrderItemRequest{
			{ProductID: 5, Quantity: 3},
			{ProductID: 2, Quantity: 1},
		}},
	}

	for _, dto := range requests {
		if _, err := svc.CreateOrder(context.Background(), verifiedUser, dto); err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
	}
}

func TestOrderServiceCreateOrderRejectsInvalidRequest(t *testing.T) {
	tests := []struct {
		name string
		user *user.UserResponse
		dto  *request.CreateOrderRequest
	}{
		{
			name: "nil user",
			dto:  &request.CreateOrderRequest{Items: []request.CreateOrderItemRequest{{ProductID: 1, Quantity: 1}}},
		},
		{
			name: "invalid user ID",
			user: &user.UserResponse{UserId: 0},
			dto:  &request.CreateOrderRequest{Items: []request.CreateOrderItemRequest{{ProductID: 1, Quantity: 1}}},
		},
		{
			name: "nil request",
			user: &user.UserResponse{UserId: 10},
		},
		{
			name: "empty items",
			user: &user.UserResponse{UserId: 10},
			dto:  &request.CreateOrderRequest{},
		},
		{
			name: "invalid product ID",
			user: &user.UserResponse{UserId: 10},
			dto:  &request.CreateOrderRequest{Items: []request.CreateOrderItemRequest{{ProductID: 0, Quantity: 1}}},
		},
		{
			name: "invalid quantity",
			user: &user.UserResponse{UserId: 10},
			dto:  &request.CreateOrderRequest{Items: []request.CreateOrderItemRequest{{ProductID: 1, Quantity: 0}}},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			mock := &mockOrderClient{
				createOrderFunc: func(
					context.Context,
					string,
					[]*order.OrderItemRequest,
					*user.UserResponse,
				) (*order.OrderResponse, error) {
					t.Fatal("Order Service should not be called for invalid input")
					return nil, nil
				},
			}

			svc := NewOrderService(mock, "test-secret")
			_, err := svc.CreateOrder(context.Background(), tt.user, tt.dto)
			if !errors.Is(err, ErrInvalidOrderRequest) {
				t.Fatalf("expected ErrInvalidOrderRequest, got %v", err)
			}
		})
	}
}

func TestOrderServiceCreateOrderPropagatesClientError(t *testing.T) {
	expectedErr := errors.New("Order Service unavailable")
	mock := &mockOrderClient{
		createOrderFunc: func(
			context.Context,
			string,
			[]*order.OrderItemRequest,
			*user.UserResponse,
		) (*order.OrderResponse, error) {
			return nil, expectedErr
		},
	}

	svc := NewOrderService(mock, "test-secret")
	_, err := svc.CreateOrder(
		context.Background(),
		&user.UserResponse{UserId: 10},
		&request.CreateOrderRequest{
			Items: []request.CreateOrderItemRequest{{ProductID: 1, Quantity: 1}},
		},
	)
	if !errors.Is(err, expectedErr) {
		t.Fatalf("expected client error, got %v", err)
	}
}

func TestOrderServiceCreateOrderRejectsEmptyResponse(t *testing.T) {
	mock := &mockOrderClient{
		createOrderFunc: func(
			context.Context,
			string,
			[]*order.OrderItemRequest,
			*user.UserResponse,
		) (*order.OrderResponse, error) {
			return nil, nil
		},
	}

	svc := NewOrderService(mock, "test-secret")
	_, err := svc.CreateOrder(
		context.Background(),
		&user.UserResponse{UserId: 10},
		&request.CreateOrderRequest{
			Items: []request.CreateOrderItemRequest{{ProductID: 1, Quantity: 1}},
		},
	)
	if !errors.Is(err, ErrEmptyOrderResponse) {
		t.Fatalf("expected ErrEmptyOrderResponse, got %v", err)
	}
}

func TestOrderServiceGetOrder(t *testing.T) {
	expectedResponse := &order.OrderResponse{
		OrderId: 101,
		UserId:  10,
	}
	mock := &mockOrderClient{
		getOrderFunc: func(
			_ context.Context,
			orderID int64,
			userID int64,
		) (*order.OrderResponse, error) {
			if orderID != 101 {
				t.Fatalf("expected order ID 101, got %d", orderID)
			}
			if userID != 10 {
				t.Fatalf("expected authenticated user ID 10, got %d", userID)
			}
			return expectedResponse, nil
		},
	}

	svc := NewOrderService(mock, "test-secret")
	got, err := svc.GetOrder(
		context.Background(),
		101,
		&user.UserResponse{UserId: 10},
	)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != expectedResponse {
		t.Fatal("expected service to return the Order Service response")
	}
}

func TestOrderServiceGetOrderRejectsInvalidInput(t *testing.T) {
	tests := []struct {
		name    string
		orderID int64
		user    *user.UserResponse
	}{
		{name: "invalid order ID", orderID: 0, user: &user.UserResponse{UserId: 10}},
		{name: "nil user", orderID: 101},
		{name: "invalid user ID", orderID: 101, user: &user.UserResponse{UserId: 0}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			mock := &mockOrderClient{
				getOrderFunc: func(
					context.Context,
					int64,
					int64,
				) (*order.OrderResponse, error) {
					t.Fatal("Order Service should not be called for invalid input")
					return nil, nil
				},
			}

			svc := NewOrderService(mock, "test-secret")
			_, err := svc.GetOrder(context.Background(), tt.orderID, tt.user)
			if err == nil {
				t.Fatal("expected validation error")
			}
		})
	}
}

func TestOrderServiceGetOrderRejectsEmptyResponse(t *testing.T) {
	mock := &mockOrderClient{
		getOrderFunc: func(
			context.Context,
			int64,
			int64,
		) (*order.OrderResponse, error) {
			return nil, nil
		},
	}

	svc := NewOrderService(mock, "test-secret")
	_, err := svc.GetOrder(
		context.Background(),
		101,
		&user.UserResponse{UserId: 10},
	)
	if !errors.Is(err, ErrEmptyOrderResponse) {
		t.Fatalf("expected ErrEmptyOrderResponse, got %v", err)
	}
}
