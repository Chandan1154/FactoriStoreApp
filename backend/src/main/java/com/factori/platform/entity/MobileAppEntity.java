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
@Table(name = "mobile_apps")
public class MobileAppEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private RetailerEntity retailer;

    @Column(name = "application_id", nullable = false, unique = true, length = 200)
    private String applicationId;

    @Column(name = "store_name", nullable = false, length = 64)
    private String storeName;

    @Column(name = "store_url", nullable = false, length = 2048)
    private String storeUrl;

    @Column(name = "icon_storage_key", nullable = false, length = 500)
    private String iconStorageKey;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "version_name", nullable = false, length = 40)
    private String versionName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MobileAppEntity() {
    }

    public MobileAppEntity(
            UUID id,
            RetailerEntity retailer,
            String applicationId,
            String storeName,
            String storeUrl,
            String iconStorageKey) {
        this.id = id;
        this.retailer = retailer;
        this.applicationId = applicationId;
        this.storeName = storeName;
        this.storeUrl = storeUrl;
        this.iconStorageKey = iconStorageKey;
        this.status = "QUEUED";
        this.versionCode = 1;
        this.versionName = "1.0";
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

    public RetailerEntity getRetailer() {
        return retailer;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getStoreUrl() {
        return storeUrl;
    }

    public String getIconStorageKey() {
        return iconStorageKey;
    }

    public String getStatus() {
        return status;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void advanceVersion() {
        versionCode++;
        versionName = "1." + (versionCode - 1);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
