package it.gestionale.api.customers;

import java.time.Clock;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.web.PageDto;
import org.springframework.data.domain.Pageable;
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

    /**
     * Clienti attivi del tenant (ADR 0014): i record cancellati logicamente non compaiono.
     * {@code q}, se non vuoto, filtra per nome o email (ILIKE).
     */
    @Transactional(readOnly = true)
    public PageDto<CustomerDto> list(Pageable pageable, String q) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim() + "%";
        return PageDto.of(customers.search(TenantContext.require(), pattern, pageable), CustomerDto::from);
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
