package service

import (
	"context"
	"errors"
	"sort"
	"strings"

	"BFF/internal/dto/request"
	order "BFF/internal/generated/order"
	user "BFF/internal/generated/user"
	"BFF/internal/mapper"
	"BFF/internal/util"
)

var (
	ErrInvalidOrderRequest = errors.New("invalid order request")
	ErrInvalidOrderID      = errors.New("order ID must be positive")
	ErrEmptyOrderResponse  = errors.New("empty Order Service response")
)

type OrderClient interface {
	CreateOrder(
		ctx context.Context,
		idempotencyKey string,
		items []*order.OrderItemRequest,
		verifiedUser *user.UserResponse,
	) (*order.OrderResponse, error)

	GetOrder(
		ctx context.Context,
		orderID int64,
		userID int64,
	) (*order.OrderResponse, error)
}

type OrderService struct {
	orderClient       OrderClient
	idempotencySecret string
}

func NewOrderService(
	orderClient OrderClient,
	idempotencySecret string,
) *OrderService {
	return &OrderService{
		orderClient:       orderClient,
		idempotencySecret: idempotencySecret,
	}
}

func (s *OrderService) CreateOrder(
	ctx context.Context,
	verifiedUser *user.UserResponse,
	dto *request.CreateOrderRequest,
) (*order.OrderResponse, error) {
	if verifiedUser == nil || verifiedUser.GetUserId() <= 0 {
		return nil, ErrInvalidOrderRequest
	}

	if dto == nil || len(dto.Items) == 0 {
		return nil, ErrInvalidOrderRequest
	}

	canonicalItems := make(
		[]request.CreateOrderItemRequest,
		len(dto.Items),
	)
	copy(canonicalItems, dto.Items)

	sort.Slice(canonicalItems, func(i, j int) bool {
		if canonicalItems[i].ProductID == canonicalItems[j].ProductID {
			return canonicalItems[i].Quantity < canonicalItems[j].Quantity
		}

		return canonicalItems[i].ProductID < canonicalItems[j].ProductID
	})

	fingerprintItems := make(
		[]util.OrderItemFingerprint,
		0,
		len(canonicalItems),
	)

	for _, item := range canonicalItems {
		if item.ProductID <= 0 || item.Quantity <= 0 {
			return nil, ErrInvalidOrderRequest
		}

		fingerprintItems = append(
			fingerprintItems,
			util.OrderItemFingerprint{
				ProductID: item.ProductID,
				Quantity:  item.Quantity,
			},
		)
	}

	idempotencyKey, err := util.GenerateOrderIdempotencyKey(
		verifiedUser.GetUserId(),
		fingerprintItems,
		s.idempotencySecret,
	)
	if err != nil {
		return nil, err
	}

	grpcItems := mapper.OrderItemsToGRPC(canonicalItems)

	result, err := s.orderClient.CreateOrder(
		ctx,
		idempotencyKey,
		grpcItems,
		verifiedUser,
	)
	if err != nil {
		return nil, err
	}

	if result == nil {
		return nil, ErrEmptyOrderResponse
	}

	return result, nil
}

func (s *OrderService) GetOrder(
	ctx context.Context,
	orderID int64,
	verifiedUser *user.UserResponse,
) (*order.OrderResponse, error) {
	if orderID <= 0 {
		return nil, ErrInvalidOrderID
	}

	if verifiedUser == nil || verifiedUser.GetUserId() <= 0 {
		return nil, ErrInvalidOrderRequest
	}

	result, err := s.orderClient.GetOrder(
		ctx,
		orderID,
		verifiedUser.GetUserId(),
	)
	if err != nil {
		return nil, err
	}

	if result == nil {
		return nil, ErrEmptyOrderResponse
	}

	return result, nil
}

// ValidateOrderIdempotencySecret can be used during startup
// to fail fast if the required secret is missing.
func ValidateOrderIdempotencySecret(secret string) error {
	if strings.TrimSpace(secret) == "" {
		return util.ErrMissingOrderIdempotencySecret
	}

	return nil
}
