package com.factori.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_versions")
public class AppVersionEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private RetailerEntity retailer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mobile_app_id", nullable = false)
    private MobileAppEntity mobileApp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_build_id", nullable = false)
    private AppBuildEntity build;

    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "version_name", nullable = false, length = 40)
    private String versionName;

    @Column(name = "artifact_sha256", nullable = false, length = 64)
    private String artifactSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppVersionEntity() {
    }

    public AppVersionEntity(
            UUID id,
            RetailerEntity retailer,
            MobileAppEntity mobileApp,
            AppBuildEntity build,
            int versionCode,
            String versionName,
            String artifactSha256) {
        this.id = id;
        this.retailer = retailer;
        this.mobileApp = mobileApp;
        this.build = build;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.artifactSha256 = artifactSha256;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
