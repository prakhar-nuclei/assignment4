package com.nuclei.orderservice.validator;

import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderItemRequestDto;
import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import java.util.List;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class CreateOrderRequestValidator {

    public void validate(final CreateOrderRequestDto request) {
        validateRequest(request);
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
}