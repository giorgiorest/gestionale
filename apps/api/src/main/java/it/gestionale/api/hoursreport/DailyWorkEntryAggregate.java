package it.gestionale.api.hoursreport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Proiezione di {@link HoursReportRepository}: somma delle ore di un giorno/risorsa/lavorazione. */
public interface DailyWorkEntryAggregate {

    LocalDate getDate();

    UUID getResourceId();

    UUID getWorkActivityId();

    BigDecimal getHours();

    BigDecimal getOvertimeHours();
}
