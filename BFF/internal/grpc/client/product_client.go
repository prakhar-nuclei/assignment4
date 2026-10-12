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

func (c *ProductClient) UpdateStock(
	ctx context.Context,
	productID int64,
	quantity int32,
	operationID string,
	direction product.StockOperationDirection,
) (*product.ProductResponse, error) {
	return c.client.UpdateStock(
		ctx,
		&product.UpdateStockRequest{
			ProductId:   productID,
			Quantity:    quantity,
			OperationId: operationID,
			Direction:   direction,
		},
	)
}
