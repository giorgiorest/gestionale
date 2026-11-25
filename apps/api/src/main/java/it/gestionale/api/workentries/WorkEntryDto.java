package it.gestionale.api.workentries;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di una riga di ore nell'API.
 *
 * @param version versione del record per il blocco ottimistico
 */
public record WorkEntryDto(UUID id, UUID dailyReportId, UUID resourceId, UUID workActivityId, BigDecimal hours,
                           BigDecimal overtimeHours, String notes, long version, Instant updatedAt) {

    static WorkEntryDto from(WorkEntry entry) {
        return new WorkEntryDto(entry.getId(), entry.getDailyReportId(), entry.getResourceId(),
                entry.getWorkActivityId(), entry.getHours(), entry.getOvertimeHours(), entry.getNotes(),
                entry.getVersion(), entry.getUpdatedAt());
    }

    /** Dati in ingresso per creare o aggiornare una riga di ore. */
    public record Input(@NotNull UUID dailyReportId, @NotNull UUID resourceId, @NotNull UUID workActivityId,
                        @NotNull @PositiveOrZero BigDecimal hours, @NotNull @PositiveOrZero BigDecimal overtimeHours,
                        @Size(max = 2000) String notes) {
    }
}
