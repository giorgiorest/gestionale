package it.gestionale.api.customers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
