package com.factori.platform.service;

import com.factori.platform.entity.AppBuildEntity;
import com.factori.platform.entity.AppVersionEntity;
import com.factori.platform.entity.MobileAppEntity;
import com.factori.platform.repository.AppBuildRepository;
import com.factori.platform.repository.AppVersionRepository;
import com.factori.platform.repository.MobileAppRepository;
import com.factori.platform.security.TenantDatabaseContext;
import com.factori.platform.service.PlatformExceptions.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuildStateService {
    private final TenantDatabaseContext tenantDatabaseContext;
    private final AppBuildRepository appBuildRepository;
    private final MobileAppRepository mobileAppRepository;
    private final AppVersionRepository appVersionRepository;

    public BuildStateService(
            TenantDatabaseContext tenantDatabaseContext,
            AppBuildRepository appBuildRepository,
            MobileAppRepository mobileAppRepository,
            AppVersionRepository appVersionRepository) {
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.appBuildRepository = appBuildRepository;
        this.mobileAppRepository = mobileAppRepository;
        this.appVersionRepository = appVersionRepository;
    }

    @Transactional
    public BuildContext start(UUID tenantId, UUID buildId) {
        tenantDatabaseContext.activate(tenantId);
        AppBuildEntity build = findBuild(tenantId, buildId);
        if (!build.getStatus().equals("QUEUED")) {
            throw new IllegalStateException("Build is not queued.");
        }
        MobileAppEntity app = build.getMobileApp();
        build.markBuilding();
        app.setStatus("BUILDING");
        return new BuildContext(
                tenantId,
                app.getId(),
                build.getId(),
                app.getApplicationId(),
                app.getStoreName(),
                app.getStoreUrl(),
                app.getIconStorageKey(),
                app.getVersionCode(),
                app.getVersionName());
    }

    @Transactional
    public void succeed(UUID tenantId, UUID buildId, BuildExecutionResult result) {
        tenantDatabaseContext.activate(tenantId);
        AppBuildEntity build = findBuild(tenantId, buildId);
        MobileAppEntity app = build.getMobileApp();
        build.markSucceeded(
                result.artifactStorageKey(),
                result.artifactSha256(),
                result.logStorageKey());
        app.setStatus("READY");
        appVersionRepository.save(new AppVersionEntity(
                UUID.randomUUID(),
                build.getRetailer(),
                app,
                build,
                app.getVersionCode(),
                app.getVersionName(),
                result.artifactSha256()));
    }

    @Transactional
    public void fail(UUID tenantId, UUID buildId, String failureCode, String logStorageKey) {
        tenantDatabaseContext.activate(tenantId);
        AppBuildEntity build = findBuild(tenantId, buildId);
        build.markFailed(failureCode, logStorageKey);
        build.getMobileApp().setStatus("FAILED");
    }

    private AppBuildEntity findBuild(UUID tenantId, UUID buildId) {
        return appBuildRepository.findByIdAndRetailer_Id(buildId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Build"));
    }
}
