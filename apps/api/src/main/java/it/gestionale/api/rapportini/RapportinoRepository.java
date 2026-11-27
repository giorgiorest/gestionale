package it.gestionale.api.rapportini;

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
 * Soggetto a RLS: restituisce solo i rapportini del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare o
 * riconoscere una cancellazione già avvenuta.
 */
public interface RapportinoRepository extends JpaRepository<Rapportino, UUID> {

    List<Rapportino> findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByDateDesc(UUID commessaId, UUID tenantId);

    List<Rapportino> findByCommessaIdAndTenantIdAndDateAndDeletedAtIsNull(UUID commessaId, UUID tenantId,
            LocalDate date);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Rapportino> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Modifiche (comprese le cancellazioni logiche) dopo il cursore, per {@code GET /api/sync/changes}. */
    @Query("""
            select e from Rapportino e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<Rapportino> findChangesSince(@Param("tenantId") UUID tenantId,
            @Param("afterUpdatedAt") Instant afterUpdatedAt, @Param("afterId") UUID afterId, Pageable pageable);
}
