package it.gestionale.api.resourceassignments;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Le assegnazioni valide (vedi {@link #existsValidAssignment}), più recenti prima: usato da
     * {@code WorkEntryService} alla chiusura del rapportino per leggere la tariffa oraria da
     * congelare nello snapshot del costo (#51). Più di una assegnazione valida sulla stessa
     * commessa/data non dovrebbe succedere, ma l'ordinamento rende deterministica la scelta.
     */
    @Query("""
            select a from ResourceAssignment a
            where a.resourceId = :resourceId and a.commessaId = :commessaId and a.tenantId = :tenantId
              and a.active = true and a.deletedAt is null
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            order by a.validFrom desc
            """)
    List<ResourceAssignment> findValidAssignments(@Param("resourceId") UUID resourceId,
            @Param("commessaId") UUID commessaId, @Param("date") LocalDate date, @Param("tenantId") UUID tenantId);
}
