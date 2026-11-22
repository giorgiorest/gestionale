package it.gestionale.api.customers;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Soggetto a RLS: restituisce solo i clienti del tenant corrente. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    long countByTenantId(UUID tenantId);

    void deleteByTenantId(UUID tenantId);
}
