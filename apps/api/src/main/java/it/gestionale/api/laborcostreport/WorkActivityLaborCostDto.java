package it.gestionale.api.laborcostreport;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ore e costo manodopera di una lavorazione nel periodo. {@code cost} è {@code null} quando
 * nessuna riga ha un costo noto (vedi {@link WorkActivityLaborCostAggregate}). Il nome della
 * lavorazione non è incluso: il frontend lo risolve dalla copia locale già sincronizzata di
 * {@code work-activities} (ADR 0020 punto 5, come {@code hoursreport}).
 */
public record WorkActivityLaborCostDto(UUID workActivityId, BigDecimal hours, BigDecimal cost) {
}
