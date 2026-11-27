package it.gestionale.api.fornitori;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Soggetto a RLS: restituisce solo i fornitori del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare o
 * riconoscere una cancellazione già avvenuta.
 */
public interface FornitoreRepository extends JpaRepository<Fornitore, UUID> {

    long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

    /** Lista paginata, ordinata e ricercabile (ADR 0014), come {@code ClienteRepository.search}. */
    @Query("""
            select e from Fornitore e
            where e.tenantId = :tenantId
              and e.deletedAt is null
              and (:pattern is null or e.name ilike :pattern or e.email ilike :pattern)
            """)
    Page<Fornitore> search(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable pageable);

    /** Anche se cancellato. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Fornitore> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Cancellazione fisica, usata solo dal ripristino del tenant demo. */
    void deleteByTenantId(UUID tenantId);

    /** Modifiche (comprese le cancellazioni logiche) dopo il cursore, per {@code GET /api/sync/changes}. */
    @Query("""
            select e from Fornitore e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<Fornitore> findChangesSince(@Param("tenantId") UUID tenantId, @Param("afterUpdatedAt") Instant afterUpdatedAt,
            @Param("afterId") UUID afterId, Pageable pageable);
}
