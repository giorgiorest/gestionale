package it.gestionale.api.customers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Soggetto a RLS: restituisce solo i clienti del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare o
 * riconoscere una cancellazione già avvenuta.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

    List<Customer> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(UUID tenantId);

    /** Anche se cancellato. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Cancellazione fisica, usata solo dal ripristino del tenant demo. */
    void deleteByTenantId(UUID tenantId);

    /**
     * Modifiche (comprese le cancellazioni logiche) dopo il cursore {@code (afterUpdatedAt, afterId)},
     * per {@code GET /api/sync/changes} (voce 1.2): a differenza dei metodi sopra, include anche i
     * tombstone, altrimenti un dispositivo offline non saprebbe di una cancellazione.
     */
    @Query("""
            select e from Customer e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<Customer> findChangesSince(@Param("tenantId") UUID tenantId, @Param("afterUpdatedAt") Instant afterUpdatedAt,
            @Param("afterId") UUID afterId, Pageable pageable);
}
