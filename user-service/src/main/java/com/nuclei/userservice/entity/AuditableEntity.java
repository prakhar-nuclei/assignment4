package com.nuclei.userservice.entity;

import com.nuclei.userservice.enums.EntityStatusEnum;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntityStatusEnum status;

    @PrePersist
    protected void onCreate() {
        final Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
        this.status = EntityStatusEnum.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
