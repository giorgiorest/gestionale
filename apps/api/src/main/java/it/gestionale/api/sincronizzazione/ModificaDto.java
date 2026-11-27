package it.gestionale.api.sincronizzazione;

import java.time.Instant;
import java.util.UUID;

/**
 * Una riga cambiata di un'entità, per {@code GET /api/sync/changes}.
 *
 * @param data i dati dell'entità (lo stesso Dto della sua API), nulli se {@code deleted}: un
 *             tombstone serve solo a dire "non esiste più", il resto non va propagato
 */
public record ModificaDto(UUID id, Instant updatedAt, long version, boolean deleted, Object data) {
}
