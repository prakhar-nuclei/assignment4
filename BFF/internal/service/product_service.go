package service

import (
	"context"
	"errors"
	"strings"

	"BFF/internal/dto/request"
	"BFF/internal/enums"
	product "BFF/internal/generated/product"
	"BFF/internal/util"
)

var (
	ErrInvalidProductID      = errors.New("product ID must be positive")
	ErrInvalidStockRequest   = errors.New("invalid stock update request")
	ErrMissingIdempotencyKey = errors.New("Idempotency-Key header is required")
	ErrEmptyProductResponse  = errors.New("empty Product Service response")
)

type ProductClient interface {
	GetProduct(
		ctx context.Context,
		productID int64,
	) (*product.ProductResponse, error)

	UpdateStock(
		ctx context.Context,
		productID int64,
		quantity int32,
		operationID string,
		direction product.StockOperationDirection,
	) (*product.ProductResponse, error)
}

type ProductService struct {
	productClient ProductClient
}

func NewProductService(
	productClient ProductClient,
) *ProductService {
	return &ProductService{
		productClient: productClient,
	}
}

func (s *ProductService) GetProduct(
	ctx context.Context,
	productID int64,
) (*product.ProductResponse, error) {
	if productID <= 0 {
		return nil, ErrInvalidProductID
	}

	result, err := s.productClient.GetProduct(ctx, productID)
	if err != nil {
		return nil, err
	}

	if result == nil {
		return nil, ErrEmptyProductResponse
	}

	return result, nil
}

func (s *ProductService) UpdateStock(
	ctx context.Context,
	userID int64,
	productID int64,
	idempotencyKey string,
	dto *request.UpdateStockRequest,
) (*product.ProductResponse, error) {
	if userID <= 0 || productID <= 0 {
		return nil, ErrInvalidProductID
	}

	if strings.TrimSpace(idempotencyKey) == "" {
		return nil, ErrMissingIdempotencyKey
	}

	if dto == nil || dto.Quantity <= 0 {
		return nil, ErrInvalidStockRequest
	}

	var direction product.StockOperationDirection

	switch dto.Direction {
	case enums.StockOperationDirectionIncrease:
		direction = product.StockOperationDirection_INCREASE
	case enums.StockOperationDirectionDecrease:
		direction = product.StockOperationDirection_DECREASE
	default:
		return nil, ErrInvalidStockRequest
	}

	operationID := util.GenerateStockOperationID(
		userID,
		idempotencyKey,
		0,
	)

	result, err := s.productClient.UpdateStock(
		ctx,
		productID,
		dto.Quantity,
		operationID,
		direction,
	)
	if err != nil {
		return nil, err
	}

	if result == nil {
		return nil, ErrEmptyProductResponse
	}

	return result, nil
}
