package com.nuclei.orderservice.grpc.metadata;

import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import io.grpc.Metadata;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class GrpcUserContext {

    public Long getUserId(final Metadata metadata) {
        validateMetadata(metadata);
        final String userIdValue = getUserIdValue(metadata);
        return parseUserId(userIdValue);
    }

    private void validateMetadata(final Metadata metadata) {
        if (metadata == null) {
            throw new InvalidOrderRequestException(
                    "gRPC metadata must not be null."
            );
        }
    }

    private String getUserIdValue(final Metadata metadata) {
        final String userIdValue =
                metadata.get(GrpcMetadataConstants.AUTHENTICATED_USER_ID);

        if (userIdValue == null || userIdValue.isBlank()) {
            throw new InvalidOrderRequestException(
                    "Authenticated user id is missing from gRPC metadata."
            );
        }

        return userIdValue;
    }

    private Long parseUserId(final String userIdValue) {
        try {
            final Long userId = Long.valueOf(userIdValue);
            validateUserId(userId);
            return userId;
        } catch (final NumberFormatException exception) {
            throw new InvalidOrderRequestException(
                    "Authenticated user id must be a valid number.",
                    exception
            );
        }
    }

    private void validateUserId(final Long userId) {
        if (userId <= 0) {
            throw new InvalidOrderRequestException(
                    "Authenticated user id must be positive."
            );
        }
    }
}