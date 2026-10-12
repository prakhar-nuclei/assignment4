package handler

import (
	"BFF/internal/generated/user"
	"errors"
	"net/http"
	"strconv"

	auth "BFF/internal/auth"
	"BFF/internal/dto/request"
	"BFF/internal/mapper"
	"BFF/internal/service"

	"github.com/gin-gonic/gin"
)

type ProductHandler struct {
	productService *service.ProductService
	authenticator  *auth.Authenticator
}

func NewProductHandler(
	productService *service.ProductService,
	authenticator *auth.Authenticator,
) *ProductHandler {
	return &ProductHandler{
		productService: productService,
		authenticator:  authenticator,
	}
}

func (h *ProductHandler) GetProduct(c *gin.Context) {
	productID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || productID <= 0 {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid product ID",
		})
		return
	}

	_, err = h.authenticateRequest(c)
	if err != nil {
		return
	}

	productResponse, err := h.productService.GetProduct(
		c.Request.Context(),
		productID,
	)
	if err != nil {
		if errors.Is(err, service.ErrEmptyProductResponse) {
			c.JSON(http.StatusBadGateway, gin.H{
				"error": "Product Service returned an invalid response",
			})
			return
		}

		writeGRPCError(c, err)
		return
	}

	c.JSON(http.StatusOK, mapper.ProductToREST(productResponse))
}

func (h *ProductHandler) UpdateStock(c *gin.Context) {
	productID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || productID <= 0 {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid product ID",
		})
		return
	}

	verifiedUser, err := h.authenticateRequest(c)
	if err != nil {
		return
	}

	idempotencyKey := c.GetHeader("Idempotency-Key")

	var dto request.UpdateStockRequest
	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid request body",
		})
		return
	}

	productResponse, err := h.productService.UpdateStock(
		c.Request.Context(),
		verifiedUser.GetUserId(),
		productID,
		idempotencyKey,
		&dto,
	)
	if err != nil {
		if errors.Is(err, service.ErrEmptyProductResponse) {
			c.JSON(http.StatusBadGateway, gin.H{
				"error": "Product Service returned an invalid response",
			})
			return
		}

		writeGRPCError(c, err)
		return
	}

	c.JSON(http.StatusOK, mapper.ProductToREST(productResponse))
}

func (h *ProductHandler) authenticateRequest(
	c *gin.Context,
) (*user.UserResponse, error) {
	{
		verifiedUser, err := h.authenticator.Authenticate(
			c.Request.Context(),
			c.GetHeader("Authorization"),
		)
		if err == nil {
			return verifiedUser, nil
		}

		switch {
		case errors.Is(err, auth.ErrMissingBearerToken),
			errors.Is(err, auth.ErrInvalidToken):
			c.JSON(http.StatusUnauthorized, gin.H{
				"error": "Authentication required",
			})
		case errors.Is(err, auth.ErrEmptyUserResponse):
			c.JSON(http.StatusBadGateway, gin.H{
				"error": "User Service returned an invalid response",
			})
		case errors.Is(err, auth.ErrUserIDMismatch):
			c.JSON(http.StatusBadGateway, gin.H{
				"error": "User Service returned an inconsistent identity",
			})
		default:
			writeGRPCError(c, err)
		}

		return nil, err
	}
}
