package it.gestionale.api.lavorazioni;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di una lavorazione nell'API. {@code status} è una stringa, non l'enum
 * {@link StatoLavorazione}: la mappatura sta nel servizio, come per {@code StatoCommessa}.
 *
 * @param version versione del record per il blocco ottimistico
 */
public record LavorazioneDto(UUID id, UUID commessaId, UUID parentId, String code, String name, String description,
                              String status, int progressPercentage, long version, Instant updatedAt) {

    private static final StatoLavorazioneConverter STATUS = new StatoLavorazioneConverter();

    public static LavorazioneDto from(Lavorazione activity) {
        return new LavorazioneDto(activity.getId(), activity.getCommessaId(), activity.getParentId(),
                activity.getCode(), activity.getName(), activity.getDescription(),
                STATUS.convertToDatabaseColumn(activity.getStatus()), activity.getProgressPercentage(),
                activity.getVersion(), activity.getUpdatedAt());
    }

    /** Dati in ingresso per creare o aggiornare una lavorazione. */
    public record Input(@NotNull UUID commessaId, UUID parentId, @NotBlank @Size(max = 50) String code,
                        @NotBlank @Size(max = 200) String name, @Size(max = 2000) String description,
                        @NotBlank @Pattern(regexp = "pianificata|in_corso|completata") String status,
                        @Min(0) @Max(100) int progressPercentage) {
    }
}
