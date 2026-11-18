package com.factori.platform.repository;

import com.factori.platform.entity.MobileAppEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobileAppRepository extends JpaRepository<MobileAppEntity, UUID> {
    Optional<MobileAppEntity> findByIdAndRetailer_Id(UUID id, UUID retailerId);

    Page<MobileAppEntity> findAllByRetailer_IdOrderByCreatedAtDesc(UUID retailerId, Pageable pageable);

    boolean existsByApplicationId(String applicationId);
}
