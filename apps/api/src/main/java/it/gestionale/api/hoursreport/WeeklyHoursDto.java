package it.gestionale.api.hoursreport;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ore totali di una settimana (vista settimanale per commessa).
 *
 * @param weekStart lunedì della settimana (settimana ISO, da lunedì a domenica)
 */
public record WeeklyHoursDto(LocalDate weekStart, BigDecimal hours, BigDecimal overtimeHours) {
}
