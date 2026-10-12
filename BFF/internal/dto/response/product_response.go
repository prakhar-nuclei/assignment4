package response

type ProductResponse struct {
	ProductID int64  `json:"product_id"`
	Name      string `json:"name"`
	Price     string `json:"price"`
	Stock     int32  `json:"stock"`
}
