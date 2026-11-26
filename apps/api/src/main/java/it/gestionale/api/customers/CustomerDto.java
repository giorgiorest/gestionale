package it.gestionale.api.customers;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di un cliente nell'API.
 *
 * @param version versione del record per il blocco ottimistico
 */
public record CustomerDto(UUID id, String name, String email, long version, Instant updatedAt) {

    public static CustomerDto from(Customer customer) {
        return new CustomerDto(customer.getId(), customer.getName(), customer.getEmail(),
                customer.getVersion(), customer.getUpdatedAt());
    }

    /** Dati in ingresso per creare o aggiornare un cliente. */
    public record Input(@NotBlank @Size(max = 200) String name, @Email @Size(max = 255) String email) {
    }
}
