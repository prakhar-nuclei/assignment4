package com.nuclei.orderservice.validator;

import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderItemRequestDto;
import com.nuclei.orderservice.dtos.UserDetailsDto;
import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import java.util.List;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class CreateOrderRequestValidator {

    public void validate(final CreateOrderRequestDto request) {
        validateRequest(request);
        validateUser(request.user());
        validateItems(request.items());
    }

    private void validateRequest(final CreateOrderRequestDto request) {
        if (request == null) {
            throw new InvalidOrderRequestException(
                    "Create order request must not be null."
            );
        }

        if (request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()) {
            throw new InvalidOrderRequestException(
                    "Idempotency key must not be blank."
            );
        }
    }

    private void validateItems(
            final List<OrderItemRequestDto> items) {

        if (items == null || items.isEmpty()) {
            throw new InvalidOrderRequestException(
                    "Order must contain at least one item."
            );
        }

        for (final OrderItemRequestDto item : items) {
            validateItem(item);
        }
    }

    private void validateItem(final OrderItemRequestDto item) {
        if (item == null) {
            throw new InvalidOrderRequestException(
                    "Order item must not be null."
            );
        }

        if (item.productId() == null || item.productId() <= 0) {
            throw new InvalidOrderRequestException(
                    "Product id must be positive."
            );
        }

        if (item.quantity() == null || item.quantity() <= 0) {
            throw new InvalidOrderRequestException(
                    "Product quantity must be positive."
            );
        }
    }

    private void validateUser(final UserDetailsDto user) {
        if (user == null) {
            throw new InvalidOrderRequestException(
                    "User details must not be null."
            );
        }

        validateUserId(user.userId());
        validateUserName(user.name());
        validateUserEmail(user.email());
    }

    private void validateUserId(final Long userId) {
        if (userId == null || userId <= 0) {
            throw new InvalidOrderRequestException(
                    "User id must be positive."
            );
        }
    }

    private void validateUserName(final String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidOrderRequestException(
                    "User name must not be blank."
            );
        }
    }

    private void validateUserEmail(final String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidOrderRequestException(
                    "User email must not be blank."
            );
        }
    }
}