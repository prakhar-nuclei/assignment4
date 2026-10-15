package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func writeGRPCError(c *gin.Context, err error) {
	httpStatus := http.StatusInternalServerError
	message := "An internal error occurred"

	if grpcStatus, ok := status.FromError(err); ok {
		switch grpcStatus.Code() {
		case codes.InvalidArgument:
			httpStatus = http.StatusBadRequest
			message = "Invalid request"

		case codes.AlreadyExists:
			httpStatus = http.StatusConflict
			message = "Resource already exists"

		case codes.Unauthenticated:
			httpStatus = http.StatusUnauthorized
			message = "Authentication required"

		case codes.PermissionDenied:
			httpStatus = http.StatusForbidden
			message = "Permission denied"

		case codes.NotFound:
			httpStatus = http.StatusNotFound
			message = "Resource not found"

		case codes.ResourceExhausted:
			httpStatus = http.StatusTooManyRequests
			message = "Request limit exceeded"

		case codes.Unavailable:
			httpStatus = http.StatusServiceUnavailable
			message = "Service temporarily unavailable"

		case codes.DeadlineExceeded:
			httpStatus = http.StatusGatewayTimeout
			message = "Upstream service timed out"

		case codes.FailedPrecondition, codes.Aborted:
			httpStatus = http.StatusConflict
			message = "Request could not be completed"

		default:
			httpStatus = http.StatusInternalServerError
			message = "An internal error occurred"
		}
	}

	c.JSON(httpStatus, gin.H{
		"error": message,
	})
}
