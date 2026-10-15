package com.nuclei.orderservice.repo;

import com.nuclei.orderservice.entity.Order;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;


public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdAndUserId(
            Long orderId,
            Long userId);
}