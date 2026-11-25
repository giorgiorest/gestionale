package it.gestionale.api.dailyreports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di un rapportino nell'API. {@code status} è una stringa, non l'enum
 * {@link DailyReportStatus}, come per {@code CommessaStato}. Non è nell'{@link Input}: questa
 * storia non lo fa mai cambiare da {@code DRAFT} (le transizioni sono un incremento successivo).
 *
 * @param version versione del record per il blocco ottimistico
 */
public record DailyReportDto(UUID id, UUID commessaId, LocalDate date, String weather, String notes, String status,
                             Instant submittedAt, Instant closedAt, long version, Instant updatedAt) {

    private static final DailyReportStatusConverter STATUS = new DailyReportStatusConverter();

    static DailyReportDto from(DailyReport report) {
        return new DailyReportDto(report.getId(), report.getCommessaId(), report.getDate(), report.getWeather(),
                report.getNotes(), STATUS.convertToDatabaseColumn(report.getStatus()), report.getSubmittedAt(),
                report.getClosedAt(), report.getVersion(), report.getUpdatedAt());
    }

    /** Dati in ingresso per creare o aggiornare la testata di un rapportino (sempre un DRAFT). */
    public record Input(@NotNull UUID commessaId, @NotNull LocalDate date, @Size(max = 100) String weather,
                        @Size(max = 2000) String notes) {
    }
}
