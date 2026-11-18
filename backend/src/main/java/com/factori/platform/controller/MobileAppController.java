package com.factori.platform.controller;

import com.factori.platform.dto.GenerateAppRequest;
import com.factori.platform.dto.MobileAppResponse;
import com.factori.platform.security.TenantIdentity;
import com.factori.platform.service.BuildJobRunner;
import com.factori.platform.service.EligibilityService;
import com.factori.platform.service.MobileAppService;
import com.factori.platform.service.MobileAppService.AppSubmission;
import com.factori.platform.service.MobileAppService.DownloadArtifact;
import com.factori.platform.service.PlatformExceptions.ConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/apps")
@Validated
public class MobileAppController {
    private final MobileAppService mobileAppService;
    private final BuildJobRunner buildJobRunner;
    private final EligibilityService eligibilityService;

    public MobileAppController(
            MobileAppService mobileAppService,
            BuildJobRunner buildJobRunner,
            EligibilityService eligibilityService) {
        this.mobileAppService = mobileAppService;
        this.buildJobRunner = buildJobRunner;
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/eligibility")
    public ResponseEntity<EligibilityService.EligibilityStatus> eligibility(
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = TenantIdentity.requireTenantId(jwt);
        return ResponseEntity.ok(eligibilityService.get(tenantId));
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MobileAppResponse> generate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("request") GenerateAppRequest request,
            @RequestPart("icon") MultipartFile icon) throws IOException {
        UUID tenantId = TenantIdentity.requireTenantId(jwt);
            if (!eligibilityService.get(tenantId).eligible()) {
                throw new ConflictException(
                        "Retailer must reach 500 completed orders or ₹250,000 in qualifying sales.");
            }
            AppSubmission submission = mobileAppService.create(
                tenantId, request, icon.getBytes());
        buildJobRunner.submit(tenantId, submission.buildId());
        return ResponseEntity.accepted()
                .location(URI.create("/api/apps/" + submission.appId()))
                .body(mobileAppService.get(tenantId, submission.appId()));
    }

    @GetMapping
    public ResponseEntity<Page<MobileAppResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        UUID tenantId = TenantIdentity.requireTenantId(jwt);
        return ResponseEntity.ok(mobileAppService.list(tenantId, page, size));
    }

    @GetMapping("/{appId}")
    public ResponseEntity<MobileAppResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appId) {
        UUID tenantId = TenantIdentity.requireTenantId(jwt);
        return ResponseEntity.ok(mobileAppService.get(tenantId, appId));
    }

    @GetMapping("/{appId}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appId) throws IOException {
        UUID tenantId = TenantIdentity.requireTenantId(jwt);
        DownloadArtifact artifact = mobileAppService.getDownload(tenantId, appId);
        Resource resource = new FileSystemResource(artifact.path());
        String filename = artifact.applicationId() + ".apk";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(artifact.path().toFile().length())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    @PostMapping("/{appId}/regenerate")
    public ResponseEntity<MobileAppResponse> regenerate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appId) {
        UUID tenantId = TenantIdentity.requireTenantId(jwt);
        AppSubmission submission = mobileAppService.regenerate(tenantId, appId);
        buildJobRunner.submit(tenantId, submission.buildId());
        return ResponseEntity.accepted()
                .location(URI.create("/api/apps/" + appId))
                .body(mobileAppService.get(tenantId, appId));
    }
}
