package com.factori.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_builds")
public class AppBuildEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private RetailerEntity retailer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mobile_app_id", nullable = false)
    private MobileAppEntity mobileApp;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "artifact_storage_key", length = 500)
    private String artifactStorageKey;

    @Column(name = "artifact_sha256", length = 64)
    private String artifactSha256;

    @Column(name = "log_storage_key", length = 500)
    private String logStorageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppBuildEntity() {
    }

    public AppBuildEntity(UUID id, RetailerEntity retailer, MobileAppEntity mobileApp) {
        this.id = id;
        this.retailer = retailer;
        this.mobileApp = mobileApp;
        this.status = "QUEUED";
        this.attempt = 1;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getArtifactStorageKey() {
        return artifactStorageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public MobileAppEntity getMobileApp() {
        return mobileApp;
    }

    public RetailerEntity getRetailer() {
        return retailer;
    }

    public void markBuilding() {
        status = "BUILDING";
        startedAt = Instant.now();
        failureCode = null;
    }

    public void markSucceeded(String artifactStorageKey, String artifactSha256, String logStorageKey) {
        status = "SUCCEEDED";
        this.artifactStorageKey = artifactStorageKey;
        this.artifactSha256 = artifactSha256;
        this.logStorageKey = logStorageKey;
        completedAt = Instant.now();
    }

    public void markFailed(String failureCode, String logStorageKey) {
        status = "FAILED";
        this.failureCode = failureCode;
        this.logStorageKey = logStorageKey;
        completedAt = Instant.now();
    }
}
