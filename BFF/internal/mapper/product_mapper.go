package mapper

import (
	response "BFF/internal/dto/response"
	product "BFF/internal/generated/product"
)

func ProductToREST(
	productResponse *product.ProductResponse,
) *response.ProductResponse {
	return &response.ProductResponse{
		ProductID: productResponse.GetProductId(),
		Name:      productResponse.GetName(),
		Price:     productResponse.GetPrice(),
		Stock:     productResponse.GetStock(),
	}
}
