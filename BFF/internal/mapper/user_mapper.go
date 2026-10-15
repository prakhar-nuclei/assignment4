package mapper

import (
	request "BFF/internal/dto/request"
	response "BFF/internal/dto/response"
	user "BFF/internal/generated/user"
)

func CreateUserToGRPC(
	dto *request.CreateUserRequest,
) *user.CreateUserRequest {
	return &user.CreateUserRequest{
		Name:     dto.Name,
		Email:    dto.Email,
		Password: dto.Password,
	}
}

func UserToREST(
	userResponse *user.UserResponse,
) *response.UserResponse {
	return &response.UserResponse{
		UserID: userResponse.GetUserId(),
		Name:   userResponse.GetName(),
		Email:  userResponse.GetEmail(),
	}
}

func AuthenticateUserToGRPC(
	dto *request.AuthenticateUserRequest,
) *user.AuthenticateUserRequest {
	return &user.AuthenticateUserRequest{
		Email:    dto.Email,
		Password: dto.Password,
	}
}
