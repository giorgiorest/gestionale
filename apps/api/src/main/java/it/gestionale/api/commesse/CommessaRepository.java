package it.gestionale.api.commesse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Soggetto a RLS: restituisce solo le commesse del tenant corrente.
 *
 * <p>I metodi di lettura escludono i record cancellati logicamente ({@code deleted_at});
 * {@link #findByIdAndTenantId} li restituisce, perché il servizio deve poterli riattivare o
 * riconoscere una cancellazione già avvenuta.
 *
 * <p>Nessun metodo di cancellazione fisica: {@code customer_id} è una chiave esterna vera ma non
 * una relazione JPA mappata, quindi Hibernate non saprebbe cancellare le commesse prima del
 * cliente che referenziano. Il ripristino del tenant demo ({@code PopolatoreTenantDemo}) le cancella
 * con un {@code DELETE} diretto, nell'ordine giusto, invece di passare da qui.
 */
public interface CommessaRepository extends JpaRepository<Commessa, UUID> {

    List<Commessa> findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(UUID tenantId);

    /** Anche se cancellata. Il filtro per tenant è difesa in profondità: la RLS già protegge. */
    Optional<Commessa> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Modifiche (comprese le cancellazioni logiche) dopo il cursore, per {@code GET /api/sync/changes}. */
    @Query("""
            select e from Commessa e
            where e.tenantId = :tenantId
              and (e.updatedAt > :afterUpdatedAt or (e.updatedAt = :afterUpdatedAt and e.id > :afterId))
            order by e.updatedAt asc, e.id asc
            """)
    List<Commessa> findChangesSince(@Param("tenantId") UUID tenantId, @Param("afterUpdatedAt") Instant afterUpdatedAt,
            @Param("afterId") UUID afterId, Pageable pageable);
}
