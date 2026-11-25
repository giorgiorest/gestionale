package it.gestionale.api.workentries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Soggetto a RLS: restituisce solo le righe di ore del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare o
 * riconoscere una cancellazione già avvenuta.
 */
public interface WorkEntryRepository extends JpaRepository<WorkEntry, UUID> {

    List<WorkEntry> findByDailyReportIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID dailyReportId,
            UUID tenantId);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<WorkEntry> findByIdAndTenantId(UUID id, UUID tenantId);
}
