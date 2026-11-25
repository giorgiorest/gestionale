package it.gestionale.api.hoursreport;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cruscotto ore di una commessa nel periodo indicato (#52): le tre viste dell'issue. */
public record HoursReportDto(UUID commessaId, LocalDate from, LocalDate to, List<WeeklyHoursDto> weekly,
                             List<ResourceHoursDto> byResource, List<WorkActivityHoursDto> byWorkActivity) {
}
