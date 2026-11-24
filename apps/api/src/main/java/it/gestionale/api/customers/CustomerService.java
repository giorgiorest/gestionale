package it.gestionale.api.customers;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    /** Clienti attivi del tenant: i record cancellati logicamente non compaiono. */
    @Transactional(readOnly = true)
    public List<CustomerDto> list() {
        return customers.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(TenantContext.require()).stream()
                .map(CustomerDto::from)
                .toList();
    }

    /**
     * Crea o aggiorna il cliente con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Un cliente cancellato logicamente torna attivo (vedi {@link Customer#update}).
     */
    @Transactional
    public CustomerDto upsert(UUID id, CustomerDto.Input input, String userId) {
        UUID tenantId = TenantContext.require();
        String name = input.name().trim();
        String email = input.email() == null || input.email().isBlank() ? null : input.email().trim();
        Customer customer = customers.findByIdAndTenantId(id, tenantId)
                .map(existing -> {
                    existing.update(name, email, userId, clock.instant());
                    return existing;
                })
                .orElseGet(() -> customers.save(new Customer(id, tenantId, name, email, userId)));
        customers.flush();
        return CustomerDto.from(customer);
    }

    /**
     * Cancellazione logica, idempotente: il record resta in tabella come tombstone, così la
     * cancellazione potrà essere propagata ai dispositivi offline. Un id sconosciuto (o di un altro
     * tenant, invisibile per la RLS) non è un errore: la outbox deve poter svuotarsi comunque.
     */
    @Transactional
    public void delete(UUID id, String userId) {
        customers.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(customer -> customer.markDeleted(userId, clock.instant()));
        customers.flush();
    }
}
