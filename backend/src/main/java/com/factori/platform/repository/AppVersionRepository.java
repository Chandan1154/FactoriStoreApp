package com.factori.platform.repository;

import com.factori.platform.entity.AppVersionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersionEntity, UUID> {
}
