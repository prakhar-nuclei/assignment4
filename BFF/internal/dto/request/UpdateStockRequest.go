package request

import "BFF/internal/enums"

type UpdateStockRequest struct {
	Quantity  int32                         `json:"quantity" binding:"required,gt=0"`
	Direction enums.StockOperationDirection `json:"direction" binding:"required,oneof=INCREASE DECREASE"`
}
