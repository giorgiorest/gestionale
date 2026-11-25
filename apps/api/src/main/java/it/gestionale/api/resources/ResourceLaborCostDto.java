package it.gestionale.api.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Tariffa oraria base di una risorsa (#53), separata da {@code ResourceDto}: dietro il permesso
 * dedicato {@code labor-cost:read/write}, non {@code resources:read/write} (vedi ADR 0021).
 */
public record ResourceLaborCostDto(UUID resourceId, BigDecimal standardHourlyCost, Instant updatedAt) {

    static ResourceLaborCostDto from(Resource resource) {
        return new ResourceLaborCostDto(resource.getId(), resource.getStandardHourlyCost(),
                resource.getUpdatedAt());
    }

    /** Dati in ingresso per impostare la tariffa oraria base. Nullable: azzera la tariffa. */
    public record Input(@PositiveOrZero BigDecimal standardHourlyCost) {
    }
}
