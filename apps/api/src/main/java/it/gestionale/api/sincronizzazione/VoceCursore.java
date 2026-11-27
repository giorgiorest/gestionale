package it.gestionale.api.sincronizzazione;

import java.time.Instant;
import java.util.UUID;

/**
 * Punto del cursore per una singola entità: l'ultima riga vista, in ordine {@code (updatedAt, id)}.
 * {@link #BEGINNING} è l'inizio dei tempi, usato quando il client non ha ancora un cursore per
 * quell'entità: evita un caso speciale nella query per la prima sincronizzazione.
 */
record VoceCursore(Instant updatedAt, UUID id) {

    static final VoceCursore BEGINNING = new VoceCursore(Instant.EPOCH, new UUID(0L, 0L));
}
