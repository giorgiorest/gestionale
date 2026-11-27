package it.gestionale.api.allegati;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Soggetto a RLS: restituisce solo gli allegati del tenant corrente. */
public interface AllegatoRepository extends JpaRepository<Allegato, UUID> {

    /** Anche se cancellato. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Allegato> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Allegati confermati e attivi di un record ("le foto di questo rapportino"), più recenti
     * prima. Un allegato ancora "in caricamento" ({@code sha256} NULL) non compare: il caricamento
     * potrebbe non arrivare mai a conferma.
     */
    List<Allegato> findByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullAndSha256IsNotNullOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);

    /** Modifiche (comprese le cancellazioni logiche) dopo il cursore, per {@code GET /api/sync/changes}. */
    @Query("""
            select e from Allegato e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<Allegato> findChangesSince(@Param("tenantId") UUID tenantId, @Param("afterUpdatedAt") Instant afterUpdatedAt,
            @Param("afterId") UUID afterId, Pageable pageable);
}
