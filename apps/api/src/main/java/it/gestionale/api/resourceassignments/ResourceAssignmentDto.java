package it.gestionale.api.resourceassignments;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di un'assegnazione nell'API.
 *
 * @param version versione del record per il blocco ottimistico
 */
public record ResourceAssignmentDto(UUID id, UUID resourceId, UUID commessaId, LocalDate validFrom,
                                    LocalDate validTo, String role, BigDecimal hourlyCostOverride, boolean active,
                                    long version, Instant updatedAt) {

    public static ResourceAssignmentDto from(ResourceAssignment a) {
        return new ResourceAssignmentDto(a.getId(), a.getResourceId(), a.getCommessaId(), a.getValidFrom(),
                a.getValidTo(), a.getRole(), a.getHourlyCostOverride(), a.isActive(), a.getVersion(),
                a.getUpdatedAt());
    }

    /** Copia senza il costo, per chi non ha {@code labor-cost:read} (vedi ADR 0021). */
    public ResourceAssignmentDto withoutHourlyCostOverride() {
        return hourlyCostOverride == null ? this
                : new ResourceAssignmentDto(id, resourceId, commessaId, validFrom, validTo, role, null, active,
                        version, updatedAt);
    }

    /** Dati in ingresso per creare o aggiornare un'assegnazione. */
    public record Input(@NotNull UUID resourceId, @NotNull UUID commessaId, @NotNull LocalDate validFrom,
                        LocalDate validTo, @NotBlank @Size(max = 100) String role,
                        @PositiveOrZero BigDecimal hourlyCostOverride, boolean active) {

        @AssertTrue(message = "validTo non può precedere validFrom")
        boolean isPeriodValid() {
            return validTo == null || !validTo.isBefore(validFrom);
        }
    }
}
