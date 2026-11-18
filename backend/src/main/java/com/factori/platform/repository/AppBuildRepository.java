package com.factori.platform.repository;

import com.factori.platform.entity.AppBuildEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppBuildRepository extends JpaRepository<AppBuildEntity, UUID> {
    Optional<AppBuildEntity> findFirstByMobileApp_IdOrderByCreatedAtDesc(UUID mobileAppId);

    Optional<AppBuildEntity> findFirstByMobileApp_IdAndStatusOrderByCreatedAtDesc(
            UUID mobileAppId, String status);

    Optional<AppBuildEntity> findByIdAndRetailer_Id(UUID id, UUID retailerId);

    List<AppBuildEntity> findAllByMobileApp_IdOrderByCreatedAtDesc(UUID mobileAppId);
}
