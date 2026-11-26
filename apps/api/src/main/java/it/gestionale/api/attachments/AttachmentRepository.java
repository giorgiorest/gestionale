package it.gestionale.api.attachments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Soggetto a RLS: restituisce solo gli allegati del tenant corrente. */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /** Anche se cancellato. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Attachment> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Allegati attivi di un record ("le foto di questo rapportino"), più recenti prima. */
    List<Attachment> findByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);

    /** Modifiche (comprese le cancellazioni logiche) dopo il cursore, per {@code GET /api/sync/changes}. */
    @Query("""
            select e from Attachment e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<Attachment> findChangesSince(@Param("tenantId") UUID tenantId, @Param("afterUpdatedAt") Instant afterUpdatedAt,
            @Param("afterId") UUID afterId, Pageable pageable);
}
