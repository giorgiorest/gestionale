package it.gestionale.api.customers;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anagrafica clienti del tenant corrente. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customers:read')")
    public List<CustomerDto> list() {
        return service.list();
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customers:write')")
    public CustomerDto upsert(@PathVariable UUID id, @Valid @RequestBody CustomerDto.Input input) {
        return service.upsert(id, input);
    }
}
