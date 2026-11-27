package it.gestionale.api.riepilogoore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ore totali di una risorsa in un mese (vista mensile per risorsa). Il nome della risorsa non è
 * incluso: il frontend lo risolve dalla copia locale già sincronizzata di {@code resources}, come
 * per le righe di ore (#50).
 *
 * @param month primo giorno del mese
 */
public record OreRisorsaDto(UUID resourceId, LocalDate month, BigDecimal hours, BigDecimal overtimeHours) {
}
