package client

import (
	"context"

	product "BFF/internal/generated/product"
)

type ProductClient struct {
	client product.ProductServiceClient
}

func NewProductClient(
	productClient product.ProductServiceClient,
) *ProductClient {
	return &ProductClient{
		client: productClient,
	}
}

func (c *ProductClient) GetProduct(
	ctx context.Context,
	productID int64,
) (*product.ProductResponse, error) {
	return c.client.GetProduct(
		ctx,
		&product.GetProductRequest{
			ProductId: productID,
		},
	)
}
