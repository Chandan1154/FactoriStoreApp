package com.factori.platform.service;

import com.factori.platform.dto.AppBuildResponse;
import com.factori.platform.dto.GenerateAppRequest;
import com.factori.platform.dto.MobileAppResponse;
import com.factori.platform.entity.AppBuildEntity;
import com.factori.platform.entity.MobileAppEntity;
import com.factori.platform.entity.RetailerEntity;
import com.factori.platform.repository.AppBuildRepository;
import com.factori.platform.repository.MobileAppRepository;
import com.factori.platform.repository.RetailerRepository;
import com.factori.platform.security.TenantDatabaseContext;
import com.factori.platform.service.PlatformExceptions.ConflictException;
import com.factori.platform.service.PlatformExceptions.ResourceNotFoundException;
import com.factori.platform.service.StoreConfigValidator.NormalizedStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobileAppService {
    private final TenantDatabaseContext tenantDatabaseContext;
    private final RetailerRepository retailerRepository;
    private final MobileAppRepository mobileAppRepository;
    private final AppBuildRepository appBuildRepository;
    private final StoreConfigValidator configValidator;
    private final ArtifactStorage artifactStorage;

    public MobileAppService(
            TenantDatabaseContext tenantDatabaseContext,
            RetailerRepository retailerRepository,
            MobileAppRepository mobileAppRepository,
            AppBuildRepository appBuildRepository,
            StoreConfigValidator configValidator,
            ArtifactStorage artifactStorage) {
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.retailerRepository = retailerRepository;
        this.mobileAppRepository = mobileAppRepository;
        this.appBuildRepository = appBuildRepository;
        this.configValidator = configValidator;
        this.artifactStorage = artifactStorage;
    }

    @Transactional
    public AppSubmission create(UUID tenantId, GenerateAppRequest request, byte[] icon)
            throws IOException {
        tenantDatabaseContext.activate(tenantId);
        RetailerEntity retailer = retailerRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Retailer"));
        NormalizedStore store = configValidator.normalize(
                request.storeName(), request.storeUrl(), tenantId);
        if (mobileAppRepository.existsByApplicationId(store.applicationId())) {
            throw new ConflictException("An app already exists for this store identity.");
        }

        UUID appId = UUID.randomUUID();
        UUID buildId = UUID.randomUUID();
        String iconKey = artifactStorage.storeIcon(appId, icon);
        MobileAppEntity app = new MobileAppEntity(
                appId,
                retailer,
                store.applicationId(),
                store.name(),
                store.url(),
                iconKey);
        mobileAppRepository.save(app);
        appBuildRepository.save(new AppBuildEntity(buildId, retailer, app));
        return new AppSubmission(appId, buildId);
    }

    @Transactional
    public AppSubmission regenerate(UUID tenantId, UUID appId) {
        tenantDatabaseContext.activate(tenantId);
        MobileAppEntity app = findApp(tenantId, appId);
        if (app.getStatus().equals("QUEUED") || app.getStatus().equals("BUILDING")) {
            throw new ConflictException("An app build is already active.");
        }
        app.advanceVersion();
        app.setStatus("QUEUED");
        UUID buildId = UUID.randomUUID();
        appBuildRepository.save(new AppBuildEntity(buildId, app.getRetailer(), app));
        return new AppSubmission(appId, buildId);
    }

    @Transactional
    public MobileAppResponse get(UUID tenantId, UUID appId) {
        tenantDatabaseContext.activate(tenantId);
        MobileAppEntity app = findApp(tenantId, appId);
        AppBuildResponse latest = appBuildRepository
                .findFirstByMobileApp_IdOrderByCreatedAtDesc(appId)
                .map(this::toResponse)
                .orElse(null);
        return toResponse(app, latest);
    }

    @Transactional
    public Page<MobileAppResponse> list(UUID tenantId, int page, int size) {
        tenantDatabaseContext.activate(tenantId);
        return mobileAppRepository.findAllByRetailer_IdOrderByCreatedAtDesc(
                        tenantId, PageRequest.of(page, size))
                .map(app -> toResponse(app, null));
    }

    @Transactional
    public DownloadArtifact getDownload(UUID tenantId, UUID appId) throws IOException {
        tenantDatabaseContext.activate(tenantId);
        MobileAppEntity app = findApp(tenantId, appId);
        AppBuildEntity build = appBuildRepository
                .findFirstByMobileApp_IdAndStatusOrderByCreatedAtDesc(appId, "SUCCEEDED")
                .orElseThrow(() -> new ResourceNotFoundException("APK"));
        Path path;
        try {
            path = artifactStorage.artifactPath(build.getArtifactStorageKey());
        } catch (IOException exception) {
            throw new ResourceNotFoundException("APK");
        }
        return new DownloadArtifact(path, app.getApplicationId(), build.getId());
    }

    private MobileAppEntity findApp(UUID tenantId, UUID appId) {
        return mobileAppRepository.findByIdAndRetailer_Id(appId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("App"));
    }

    private MobileAppResponse toResponse(MobileAppEntity app, AppBuildResponse latestBuild) {
        return new MobileAppResponse(
                app.getId(),
                app.getApplicationId(),
                app.getStoreName(),
                app.getStoreUrl(),
                app.getStatus(),
                app.getCreatedAt(),
                latestBuild);
    }

    private AppBuildResponse toResponse(AppBuildEntity build) {
        return new AppBuildResponse(
                build.getId(),
                build.getStatus(),
                build.getAttempt(),
                build.getFailureCode(),
                build.getCreatedAt(),
                build.getCompletedAt());
    }

    public record AppSubmission(UUID appId, UUID buildId) {
    }

    public record DownloadArtifact(Path path, String applicationId, UUID buildId) {
    }
}
