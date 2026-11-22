package it.gestionale.api.ai;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

    long countByTenantIdAndCreatedAtGreaterThanEqual(UUID tenantId, Instant from);
}
