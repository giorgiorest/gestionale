package it.gestionale.api.riepilogoore;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ore totali di una settimana (vista settimanale per commessa).
 *
 * @param weekStart lunedì della settimana (settimana ISO, da lunedì a domenica)
 */
public record OreSettimanaliDto(LocalDate weekStart, BigDecimal hours, BigDecimal overtimeHours) {
}
