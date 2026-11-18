package com.factori.platform.repository;

import com.factori.platform.entity.RetailerEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailerRepository extends JpaRepository<RetailerEntity, UUID> {
}
