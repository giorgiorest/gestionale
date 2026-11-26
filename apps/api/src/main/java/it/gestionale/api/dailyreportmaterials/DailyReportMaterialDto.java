package it.gestionale.api.dailyreportmaterials;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di una riga di materiale nell'API.
 *
 * @param version versione del record per il blocco ottimistico
 */
public record DailyReportMaterialDto(UUID id, UUID dailyReportId, String description, BigDecimal quantity,
                                     String unit, String notes, long version, Instant updatedAt) {

    public static DailyReportMaterialDto from(DailyReportMaterial material) {
        return new DailyReportMaterialDto(material.getId(), material.getDailyReportId(), material.getDescription(),
                material.getQuantity(), material.getUnit(), material.getNotes(), material.getVersion(),
                material.getUpdatedAt());
    }

    /** Dati in ingresso per creare o aggiornare una riga di materiale. */
    public record Input(@NotNull UUID dailyReportId, @NotBlank @Size(max = 255) String description,
                        @NotNull @Positive BigDecimal quantity, @NotBlank @Size(max = 50) String unit,
                        @Size(max = 2000) String notes) {
    }
}
