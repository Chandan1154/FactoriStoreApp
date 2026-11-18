package com.factori.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retailers")
public class RetailerEntity {
    @Id
    private UUID id;

    @Column(name = "external_retailer_id", nullable = false, unique = true, length = 200)
    private String externalRetailerId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RetailerEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}
