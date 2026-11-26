package it.gestionale.api.resources;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di una risorsa nell'API.
 *
 * @param version versione del record per il blocco ottimistico
 */
public record ResourceDto(UUID id, String firstName, String lastName, String userId, UUID teamLeadId, long version,
                          Instant updatedAt) {

    public static ResourceDto from(Resource resource) {
        return new ResourceDto(resource.getId(), resource.getFirstName(), resource.getLastName(),
                resource.getUserId(), resource.getTeamLeadId(), resource.getVersion(), resource.getUpdatedAt());
    }

    /** Dati in ingresso per creare o aggiornare una risorsa. */
    public record Input(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
                        @Size(max = 255) String userId, UUID teamLeadId) {
    }
}
