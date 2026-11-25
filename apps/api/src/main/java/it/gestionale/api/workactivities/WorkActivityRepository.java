package it.gestionale.api.workactivities;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Soggetto a RLS: restituisce solo le lavorazioni del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare,
 * riconoscere una cancellazione già avvenuta, o verificare un {@code parentId} indicato.
 */
public interface WorkActivityRepository extends JpaRepository<WorkActivity, UUID> {

    List<WorkActivity> findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByCodeAsc(UUID commessaId, UUID tenantId);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<WorkActivity> findByIdAndTenantId(UUID id, UUID tenantId);
}
