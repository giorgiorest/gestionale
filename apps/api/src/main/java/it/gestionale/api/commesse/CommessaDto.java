package it.gestionale.api.commesse;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di una commessa nell'API. {@code stato} è una stringa, non l'enum
 * {@link CommessaStato}: la mappatura sta nel servizio, come per {@code TenantStatus} in
 * {@code PlatformService} (il confine dell'API non espone l'enum Java direttamente).
 *
 * @param version versione del record per il blocco ottimistico
 */
public record CommessaDto(UUID id, UUID customerId, String nome, String indirizzo, String stato, long version,
                          Instant updatedAt) {

    static CommessaDto from(Commessa commessa) {
        return new CommessaDto(commessa.getId(), commessa.getCustomerId(), commessa.getNome(),
                commessa.getIndirizzo(), code(commessa.getStato()), commessa.getVersion(), commessa.getUpdatedAt());
    }

    static String code(CommessaStato stato) {
        return stato.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Dati in ingresso per creare o aggiornare una commessa. */
    public record Input(@NotNull UUID customerId, @NotBlank @Size(max = 200) String nome,
                        @Size(max = 300) String indirizzo,
                        @NotBlank @Pattern(regexp = "pianificata|in_corso|sospesa|conclusa") String stato) {
    }
}
