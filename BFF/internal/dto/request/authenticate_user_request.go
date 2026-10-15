package request

type AuthenticateUserRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}
