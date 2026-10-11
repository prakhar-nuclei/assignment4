package com.nuclei.orderservice.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class StockOperationIdUtils {

    private StockOperationIdUtils() {
    }

    public static String generateOperationId(
            final Long userId,
            final String idempotencyKey,
            final int itemIndex) {

        final String source = userId + ":"
                + idempotencyKey + ":"
                + itemIndex;

        return UUID.nameUUIDFromBytes(
                source.getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    public static String generateCompensationId(
            final String operationId) {

        return UUID.nameUUIDFromBytes(
                (operationId + ":compensation")
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}