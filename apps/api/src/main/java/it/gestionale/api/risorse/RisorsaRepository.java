package it.gestionale.api.risorse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Soggetto a RLS: restituisce solo le risorse del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare,
 * riconoscere una cancellazione già avvenuta, o verificare un {@code teamLeadId} indicato.
 */
public interface RisorsaRepository extends JpaRepository<Risorsa, UUID> {

    List<Risorsa> findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(UUID tenantId);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Risorsa> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Vero se la risorsa è già capo squadra di qualcuno: non può anche diventare membro. */
    boolean existsByTeamLeadIdAndTenantIdAndDeletedAtIsNull(UUID teamLeadId, UUID tenantId);

    /** Cancellazione fisica, usata solo dal ripristino del tenant demo. */
    void deleteByTenantId(UUID tenantId);

    /** Modifiche (comprese le cancellazioni logiche) dopo il cursore, per {@code GET /api/sync/changes}. */
    @Query("""
            select e from Risorsa e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<Risorsa> findChangesSince(@Param("tenantId") UUID tenantId, @Param("afterUpdatedAt") Instant afterUpdatedAt,
            @Param("afterId") UUID afterId, Pageable pageable);
}
