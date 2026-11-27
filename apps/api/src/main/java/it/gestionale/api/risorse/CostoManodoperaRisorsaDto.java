package it.gestionale.api.risorse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Tariffa oraria base di una risorsa (#53), separata da {@code RisorsaDto}: dietro il permesso
 * dedicato {@code labor-cost:read/write}, non {@code resources:read/write} (vedi ADR 0021).
 */
public record CostoManodoperaRisorsaDto(UUID resourceId, BigDecimal standardHourlyCost, Instant updatedAt) {

    static CostoManodoperaRisorsaDto from(Risorsa resource) {
        return new CostoManodoperaRisorsaDto(resource.getId(), resource.getStandardHourlyCost(),
                resource.getUpdatedAt());
    }

    /** Dati in ingresso per impostare la tariffa oraria base. Nullable: azzera la tariffa. */
    public record Input(@PositiveOrZero BigDecimal standardHourlyCost) {
    }
}
