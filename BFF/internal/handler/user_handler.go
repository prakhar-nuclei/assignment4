package handler

import (
	"errors"
	"net/http"
	"strconv"

	auth "BFF/internal/auth"
	"BFF/internal/dto/request"
	"BFF/internal/mapper"
	"BFF/internal/service"

	"github.com/gin-gonic/gin"
)

type UserHandler struct {
	userService   *service.UserService
	authenticator *auth.Authenticator
}

func NewUserHandler(
	userService *service.UserService,
	authenticator *auth.Authenticator,
) *UserHandler {
	return &UserHandler{
		userService:   userService,
		authenticator: authenticator,
	}
}

func (h *UserHandler) CreateUser(c *gin.Context) {
	var dto request.CreateUserRequest

	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid request body",
		})
		return
	}

	userResponse, err := h.userService.CreateUser(
		c.Request.Context(),
		&dto,
	)
	if err != nil {
		if errors.Is(err, service.ErrEmptyUserResponse) {
			c.JSON(http.StatusBadGateway, gin.H{
				"error": "User Service returned an invalid response",
			})
			return
		}

		writeGRPCError(c, err)
		return
	}

	c.JSON(http.StatusCreated, userResponse)
}

func (h *UserHandler) AuthenticateUser(c *gin.Context) {
	var dto request.AuthenticateUserRequest

	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid request body",
		})
		return
	}

	tokenResponse, err := h.userService.AuthenticateUser(
		c.Request.Context(),
		&dto,
	)
	if err != nil {
		if errors.Is(err, service.ErrEmptyUserResponse) {
			c.JSON(http.StatusBadGateway, gin.H{
				"error": "User Service returned an invalid response",
			})
			return
		}

		writeGRPCError(c, err)
		return
	}

	c.JSON(http.StatusOK, tokenResponse)
}

func (h *UserHandler) GetUser(c *gin.Context) {
	userID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || userID <= 0 {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid user ID",
		})
		return
	}

	verifiedUser, err := h.authenticator.Authenticate(
		c.Request.Context(),
		c.GetHeader("Authorization"),
	)
	if err != nil {
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
		return
	}

	if verifiedUser.GetUserId() != userID {
		c.JSON(http.StatusForbidden, gin.H{
			"error": "Permission denied",
		})
		return
	}

	c.JSON(http.StatusOK, mapper.UserToREST(verifiedUser))
}
