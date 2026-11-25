package it.gestionale.api.hoursreport;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ore totali di una lavorazione nel periodo (aggregazione per Work Activity). Il nome della
 * lavorazione non è incluso: stesso motivo di {@link ResourceHoursDto}.
 */
public record WorkActivityHoursDto(UUID workActivityId, BigDecimal hours, BigDecimal overtimeHours) {
}
