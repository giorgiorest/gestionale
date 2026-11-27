package it.gestionale.api.sincronizzazione;

import java.util.List;
import java.util.Map;

/**
 * Risposta di {@code GET /api/sync/changes}.
 *
 * <p>{@code entities} contiene solo le entità per cui l'utente ha il permesso di lettura
 * corrispondente: l'assenza della chiave è il segnale, non un array vuoto (che sarebbe
 * indistinguibile da "nessuna modifica").
 *
 * @param cursor  da passare alla chiamata successiva; il client lo tratta come opaco
 * @param hasMore vero se almeno un'entità ha raggiunto il limite di pagina: la chiamata va
 *                ripetuta con il nuovo cursore finché non torna falso
 */
public record ModificheDto(String cursor, boolean hasMore, Map<String, List<ModificaDto>> entities) {
}
