package com.nuclei.orderservice.validator;

import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class GetOrderRequestValidator {

    public void validate(final GetOrderRequestDto request) {
        if (request == null) {
            throw new InvalidOrderRequestException(
                    "Get order request must not be null."
            );
        }

        if (request.orderId() == null || request.orderId() <= 0) {
            throw new InvalidOrderRequestException(
                    "Order id must be positive."
            );
        }
    }
}