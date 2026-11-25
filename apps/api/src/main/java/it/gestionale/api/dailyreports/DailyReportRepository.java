package it.gestionale.api.dailyreports;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Soggetto a RLS: restituisce solo i rapportini del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare o
 * riconoscere una cancellazione già avvenuta.
 */
public interface DailyReportRepository extends JpaRepository<DailyReport, UUID> {

    List<DailyReport> findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByDateDesc(UUID commessaId, UUID tenantId);

    List<DailyReport> findByCommessaIdAndTenantIdAndDateAndDeletedAtIsNull(UUID commessaId, UUID tenantId,
            LocalDate date);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<DailyReport> findByIdAndTenantId(UUID id, UUID tenantId);
}
