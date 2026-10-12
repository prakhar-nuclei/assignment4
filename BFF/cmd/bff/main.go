package main

import (
	"BFF/internal/generated/order"
	"log"
	"os"

	"BFF/internal/auth"
	"BFF/internal/generated/product"
	"BFF/internal/generated/user"
	"BFF/internal/grpc/client"
	"BFF/internal/handler"
	"BFF/internal/service"

	"github.com/gin-gonic/gin"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

func main() {
	userServiceAddress := os.Getenv("USER_SERVICE_ADDR")
	if userServiceAddress == "" {
		userServiceAddress = "localhost:6565"
	}

	idempotencySecret := os.Getenv("IDEMPOTENCY_SECRET")
	if err := service.ValidateOrderIdempotencySecret(idempotencySecret); err != nil {
		log.Fatal(err)
	}

	conn, err := grpc.NewClient(
		userServiceAddress,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		log.Fatalf("failed to create User Service connection: %v", err)
	}
	defer conn.Close()

	userGRPCClient := user.NewUserServiceClient(conn)
	userClient := client.NewUserClient(userGRPCClient)

	userService := service.NewUserService(
		userClient,
		idempotencySecret,
	)
	authenticator := auth.NewAuthenticator(userClient)
	userHandler := handler.NewUserHandler(userService, authenticator)

	productServiceAddress := os.Getenv("PRODUCT_SERVICE_ADDR")
	if productServiceAddress == "" {
		productServiceAddress = "localhost:6566"
	}

	productConn, err := grpc.NewClient(
		productServiceAddress,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		log.Fatalf("failed to create Product Service connection: %v", err)
	}
	defer productConn.Close()

	productGRPCClient := product.NewProductServiceClient(productConn)
	productClient := client.NewProductClient(productGRPCClient)
	productService := service.NewProductService(productClient)

	orderServiceAddress := os.Getenv("ORDER_SERVICE_ADDR")
	if orderServiceAddress == "" {
		orderServiceAddress = "localhost:6567"
	}

	orderConn, err := grpc.NewClient(
		orderServiceAddress,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		log.Fatalf("failed to create Order Service connection: %v", err)
	}
	defer orderConn.Close()

	orderGRPCClient := order.NewOrderServiceClient(orderConn)
	orderClient := client.NewOrderClient(orderGRPCClient)

	orderService := service.NewOrderService(
		orderClient,
		idempotencySecret,
	)

	orderHandler := handler.NewOrderHandler(
		orderService,
		authenticator,
	)

	router := gin.Default()
	router.POST("/api/users", userHandler.CreateUser)
	router.POST("/api/authenticate", userHandler.AuthenticateUser)
	router.GET("/api/users/:id", userHandler.GetUser)

	productHandler := handler.NewProductHandler(
		productService,
		authenticator,
	)

	router.GET("/api/products/:id", productHandler.GetProduct)
	router.PATCH("/api/products/:id/stock", productHandler.UpdateStock)
	router.POST("/api/orders", orderHandler.CreateOrder)
	router.GET("/api/orders/:id", orderHandler.GetOrder)

	if err := router.Run(":8080"); err != nil {
		log.Fatalf("failed to start BFF server: %v", err)
	}
}
