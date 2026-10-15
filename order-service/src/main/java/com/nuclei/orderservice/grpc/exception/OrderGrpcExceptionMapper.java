package com.nuclei.orderservice.grpc.exception;

import com.nuclei.orderservice.exception.InsufficientStockException;
import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import com.nuclei.orderservice.exception.OrderNotFoundException;
import com.nuclei.orderservice.exception.ProductConcurrencyException;
import com.nuclei.orderservice.exception.ProductNotFoundException;
import com.nuclei.orderservice.exception.UserNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class OrderGrpcExceptionMapper {

    private static final String UNEXPECTED_ERROR_MESSAGE =
            "Unexpected error while processing order request.";

    public StatusRuntimeException toStatusException(
            final Exception exception) {

        Status status = Status.INTERNAL;
        String description = UNEXPECTED_ERROR_MESSAGE;

        if (exception instanceof InvalidOrderRequestException) {
            status = Status.INVALID_ARGUMENT;
            description = exception.getMessage();
        } else if (exception instanceof UserNotFoundException
                || exception instanceof ProductNotFoundException
                || exception instanceof OrderNotFoundException) {
            status = Status.NOT_FOUND;
            description = exception.getMessage();
        } else if (exception instanceof InsufficientStockException) {
            status = Status.FAILED_PRECONDITION;
            description = exception.getMessage();
        } else if (exception instanceof ProductConcurrencyException) {
            status = Status.ABORTED;
            description = exception.getMessage();
        }

        return status
                .withDescription(description)
                .withCause(exception)
                .asRuntimeException();
    }
}