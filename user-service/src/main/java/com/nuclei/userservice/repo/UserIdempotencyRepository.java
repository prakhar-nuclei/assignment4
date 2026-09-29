package com.nuclei.userservice.repo;

import com.nuclei.userservice.entity.UserIdempotency;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdempotencyRepository
        extends JpaRepository<UserIdempotency, Long> {

    Optional<UserIdempotency> findByIdempotencyKey(
            String idempotencyKey
    );
}