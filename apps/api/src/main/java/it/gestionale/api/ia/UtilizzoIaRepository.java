package it.gestionale.api.ia;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilizzoIaRepository extends JpaRepository<UtilizzoIa, UUID> {

    long countByTenantIdAndCreatedAtGreaterThanEqual(UUID tenantId, Instant from);
}
