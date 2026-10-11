package com.nuclei.orderservice.grpc.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

class OrderGrpcExceptionMapperTest {

    private final OrderGrpcExceptionMapper mapper =
            new OrderGrpcExceptionMapper();

    @Test
    void shouldMapInvalidOrderRequestToInvalidArgument() {
        final InvalidOrderRequestException exception =
                new InvalidOrderRequestException("Invalid order request");

        final StatusRuntimeException result =
                mapper.toStatusException(exception);

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                result.getStatus().getCode());
        assertEquals(
                "Invalid order request",
                result.getStatus().getDescription());
        assertSame(exception, result.getCause());
    }

    @Test
    void shouldMapUnexpectedExceptionToInternal() {
        final RuntimeException exception =
                new RuntimeException("Database unavailable");

        final StatusRuntimeException result =
                mapper.toStatusException(exception);

        assertEquals(
                Status.Code.INTERNAL,
                result.getStatus().getCode());
        assertEquals(
                "Unexpected error while processing order request.",
                result.getStatus().getDescription());
        assertSame(exception, result.getCause());
    }
}