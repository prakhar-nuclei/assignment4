package mapper

import (
	"BFF/internal/dto/request"
	"BFF/internal/dto/response"
	order "BFF/internal/generated/order"
)

func OrderItemsToGRPC(
	items []request.CreateOrderItemRequest,
) []*order.OrderItemRequest {
	grpcItems := make([]*order.OrderItemRequest, 0, len(items))

	for _, item := range items {
		grpcItems = append(grpcItems, &order.OrderItemRequest{
			ProductId: item.ProductID,
			Quantity:  item.Quantity,
		})
	}

	return grpcItems
}

func OrderToREST(
	orderResponse *order.OrderResponse,
) *response.OrderResponse {
	if orderResponse == nil {
		return nil
	}

	grpcItems := orderResponse.GetItems()
	restItems := make([]response.OrderItemResponse, 0, len(grpcItems))

	for _, item := range grpcItems {
		if item == nil {
			continue
		}

		restItems = append(restItems, response.OrderItemResponse{
			ProductID:   item.GetProductId(),
			ProductName: item.GetProductName(),
			UnitPrice:   item.GetUnitPrice(),
			Quantity:    item.GetQuantity(),
			Subtotal:    item.GetSubtotal(),
		})
	}

	return &response.OrderResponse{
		OrderID:     orderResponse.GetOrderId(),
		UserID:      orderResponse.GetUserId(),
		Items:       restItems,
		TotalAmount: orderResponse.GetTotalAmount(),
		Status:      orderResponse.GetStatus(),
	}
}
