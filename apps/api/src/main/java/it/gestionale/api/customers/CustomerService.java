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

    @Transactional(readOnly = true)
    public List<CustomerDto> list() {
        return customers.findByTenantIdOrderByNameAsc(TenantContext.require()).stream()
                .map(CustomerDto::from)
                .toList();
    }

    /**
     * Crea o aggiorna il cliente con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     */
    @Transactional
    public CustomerDto upsert(UUID id, CustomerDto.Input input) {
        UUID tenantId = TenantContext.require();
        String email = input.email() == null || input.email().isBlank() ? null : input.email().trim();
        Customer customer = customers.findById(id)
                .map(existing -> {
                    existing.update(input.name().trim(), email, clock.instant());
                    return existing;
                })
                .orElseGet(() -> customers.save(new Customer(id, tenantId, input.name().trim(), email)));
        customers.flush();
        return CustomerDto.from(customer);
    }
}
