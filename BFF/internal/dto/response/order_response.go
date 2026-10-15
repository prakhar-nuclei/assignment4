package response

type OrderItemResponse struct {
	ProductID   int64  `json:"product_id"`
	ProductName string `json:"product_name"`
	UnitPrice   string `json:"unit_price"`
	Quantity    int32  `json:"quantity"`
	Subtotal    string `json:"subtotal"`
}

type OrderResponse struct {
	OrderID     int64               `json:"order_id"`
	UserID      int64               `json:"user_id"`
	Items       []OrderItemResponse `json:"items"`
	TotalAmount string              `json:"total_amount"`
	Status      string              `json:"status"`
}
