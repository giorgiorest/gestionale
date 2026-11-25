package it.gestionale.api.resources;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Soggetto a RLS: restituisce solo le risorse del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare,
 * riconoscere una cancellazione già avvenuta, o verificare un {@code teamLeadId} indicato.
 */
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(UUID tenantId);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Resource> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Vero se la risorsa è già capo squadra di qualcuno: non può anche diventare membro. */
    boolean existsByTeamLeadIdAndTenantIdAndDeletedAtIsNull(UUID teamLeadId, UUID tenantId);

    /** Cancellazione fisica, usata solo dal ripristino del tenant demo. */
    void deleteByTenantId(UUID tenantId);
}
