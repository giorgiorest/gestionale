package it.gestionale.api.resourceassignments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Soggetto a RLS: restituisce solo le assegnazioni del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare o
 * riconoscere una cancellazione già avvenuta.
 */
public interface ResourceAssignmentRepository extends JpaRepository<ResourceAssignment, UUID> {

    List<ResourceAssignment> findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByValidFromAsc(UUID commessaId,
            UUID tenantId);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<ResourceAssignment> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Vero se la risorsa ha un'assegnazione attiva alla commessa che comprende la data indicata
     * (usato da {@code WorkEntryService} per validare le righe di ore, #50).
     */
    @Query("""
            select count(a) > 0 from ResourceAssignment a
            where a.resourceId = :resourceId and a.commessaId = :commessaId and a.tenantId = :tenantId
              and a.active = true and a.deletedAt is null
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    boolean existsValidAssignment(@Param("resourceId") UUID resourceId, @Param("commessaId") UUID commessaId,
            @Param("date") LocalDate date, @Param("tenantId") UUID tenantId);

    /** Modifiche (comprese le cancellazioni logiche) dopo il cursore, per {@code GET /api/sync/changes}. */
    @Query("""
            select e from ResourceAssignment e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<ResourceAssignment> findChangesSince(@Param("tenantId") UUID tenantId,
            @Param("afterUpdatedAt") Instant afterUpdatedAt, @Param("afterId") UUID afterId, Pageable pageable);
}
