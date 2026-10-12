package client

import (
	"context"

	order "BFF/internal/generated/order"
	user "BFF/internal/generated/user"
)

type OrderClient struct {
	client order.OrderServiceClient
}

func NewOrderClient(
	orderClient order.OrderServiceClient,
) *OrderClient {
	return &OrderClient{
		client: orderClient,
	}
}

func (c *OrderClient) CreateOrder(
	ctx context.Context,
	idempotencyKey string,
	items []*order.OrderItemRequest,
	verifiedUser *user.UserResponse,
) (*order.OrderResponse, error) {
	return c.client.CreateOrder(ctx, &order.CreateOrderRequest{
		IdempotencyKey: idempotencyKey,
		Items:          items,
		User:           verifiedUser,
	})
}

func (c *OrderClient) GetOrder(
	ctx context.Context,
	orderID int64,
	userID int64,
) (*order.OrderResponse, error) {
	return c.client.GetOrder(ctx, &order.GetOrderRequest{
		OrderId: orderID,
		UserId:  userID,
	})
}
