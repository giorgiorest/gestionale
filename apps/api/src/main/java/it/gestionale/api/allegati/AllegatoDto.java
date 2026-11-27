package it.gestionale.api.allegati;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di un allegato nell'API. {@code storageKey} non è qui apposta: è un dettaglio
 * interno dell'archivio, il client chiede sempre un URL firmato per id (ADR 0016).
 *
 * @param version versione del record per il blocco ottimistico
 */
public record AllegatoDto(UUID id, String entityType, UUID entityId, String originalFilename, String mimeType,
                             long sizeBytes, String sha256, long version, Instant updatedAt) {

    public static AllegatoDto from(Allegato attachment) {
        return new AllegatoDto(attachment.getId(), attachment.getEntityType(), attachment.getEntityId(),
                attachment.getOriginalFilename(), attachment.getMimeType(), attachment.getSizeBytes(),
                attachment.getSha256(), attachment.getVersion(), attachment.getUpdatedAt());
    }

    /** Richiesta di un URL firmato di caricamento: l'id è generato dal client (UUID v7). */
    public record RichiestaUrlCaricamento(
            @NotNull UUID id,
            @NotBlank @Size(max = 100) String entityType,
            @NotNull UUID entityId,
            @NotBlank @Size(max = 255) String filename,
            @NotBlank @Size(max = 255) String mimeType,
            @Positive long sizeBytes) {
    }

    /** URL firmato {@code PUT}: il client ci carica i byte direttamente, senza passare dall'API. */
    public record RispostaUrlCaricamento(String uploadUrl, Instant expiresAt) {
    }

    /**
     * Conferma dopo il caricamento. Gli altri metadati (entità, nome, tipo, dimensione) sono già
     * quelli di {@link RichiestaUrlCaricamento}, fissati alla riga creata in quel passo: la conferma non
     * li porta di nuovo, altrimenti i due passi potrebbero descrivere file diversi.
     */
    public record Conferma(@NotBlank @Size(min = 64, max = 64) String sha256) {
    }

    /** URL firmato {@code GET}: il client scarica i byte direttamente, senza passare dall'API. */
    public record RispostaUrlScaricamento(String downloadUrl, Instant expiresAt) {
    }
}
