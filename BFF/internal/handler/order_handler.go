package handler

import (
	"net/http"
	"strconv"

	"BFF/internal/auth"
	"BFF/internal/dto/request"
	"BFF/internal/mapper"
	"BFF/internal/service"

	"github.com/gin-gonic/gin"
)

type OrderHandler struct {
	orderService  *service.OrderService
	authenticator *auth.Authenticator
}

func NewOrderHandler(
	orderService *service.OrderService,
	authenticator *auth.Authenticator,
) *OrderHandler {
	return &OrderHandler{
		orderService:  orderService,
		authenticator: authenticator,
	}
}

func (h *OrderHandler) CreateOrder(c *gin.Context) {
	verifiedUser, err := h.authenticator.Authenticate(
		c.Request.Context(),
		c.GetHeader("Authorization"),
	)
	if err != nil {
		writeGRPCError(c, err)
		return
	}

	var dto request.CreateOrderRequest
	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "invalid order request",
		})
		return
	}

	result, err := h.orderService.CreateOrder(
		c.Request.Context(),
		verifiedUser,
		&dto,
	)
	if err != nil {
		writeGRPCError(c, err)
		return
	}

	c.JSON(http.StatusCreated, mapper.OrderToREST(result))
}

func (h *OrderHandler) GetOrder(c *gin.Context) {
	verifiedUser, err := h.authenticator.Authenticate(
		c.Request.Context(),
		c.GetHeader("Authorization"),
	)
	if err != nil {
		writeGRPCError(c, err)
		return
	}

	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || orderID <= 0 {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "order ID must be a positive integer",
		})
		return
	}

	result, err := h.orderService.GetOrder(
		c.Request.Context(),
		orderID,
		verifiedUser,
	)
	if err != nil {
		writeGRPCError(c, err)
		return
	}

	c.JSON(http.StatusOK, mapper.OrderToREST(result))
}
