package service

import (
	"context"
	"errors"

	product "BFF/internal/generated/product"
)

var (
	ErrInvalidProductID     = errors.New("product ID must be positive")
	ErrEmptyProductResponse = errors.New("empty Product Service response")
)

type ProductClient interface {
	GetProduct(
		ctx context.Context,
		productID int64,
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
