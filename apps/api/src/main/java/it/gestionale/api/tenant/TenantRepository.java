package it.gestionale.api.tenant;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Soggetto a RLS: con un tenant nel contesto vede solo quel tenant. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
