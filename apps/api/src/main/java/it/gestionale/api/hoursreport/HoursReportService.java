package it.gestionale.api.hoursreport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cruscotto ore (#52): tre viste derivate dalla stessa aggregazione per giorno/risorsa/lavorazione. */
@Service
public class HoursReportService {

    private final HoursReportRepository repository;

    public HoursReportService(HoursReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public HoursReportDto report(UUID commessaId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidHoursReportRangeException(
                    "L'intervallo non è valido: \"a\" (" + to + ") è prima di \"da\" (" + from + ")");
        }
        List<DailyWorkEntryAggregate> rows = repository.aggregateByDateResourceAndActivity(commessaId, from, to,
                TenantContext.require());

        Map<LocalDate, Totals> byWeek = new TreeMap<>();
        Map<ResourceMonth, Totals> byResourceMonth = new HashMap<>();
        Map<UUID, Totals> byActivity = new HashMap<>();
        for (DailyWorkEntryAggregate row : rows) {
            byWeek.merge(mondayOf(row.getDate()), Totals.of(row), Totals::plus);
            byResourceMonth.merge(new ResourceMonth(row.getDate().withDayOfMonth(1), row.getResourceId()),
                    Totals.of(row), Totals::plus);
            byActivity.merge(row.getWorkActivityId(), Totals.of(row), Totals::plus);
        }

        List<WeeklyHoursDto> weekly = byWeek.entrySet().stream()
                .map(e -> new WeeklyHoursDto(e.getKey(), e.getValue().hours(), e.getValue().overtimeHours()))
                .toList();
        List<ResourceHoursDto> byResource = byResourceMonth.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<ResourceMonth, Totals> e) -> e.getKey().month())
                        .thenComparing(e -> e.getKey().resourceId()))
                .map(e -> new ResourceHoursDto(e.getKey().resourceId(), e.getKey().month(), e.getValue().hours(),
                        e.getValue().overtimeHours()))
                .toList();
        List<WorkActivityHoursDto> byWorkActivity = byActivity.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new WorkActivityHoursDto(e.getKey(), e.getValue().hours(), e.getValue().overtimeHours()))
                .toList();

        return new HoursReportDto(commessaId, from, to, weekly, byResource, byWorkActivity);
    }

    /** Lunedì della settimana ISO (da lunedì a domenica) che contiene la data. */
    private static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private record ResourceMonth(LocalDate month, UUID resourceId) {
    }

    private record Totals(BigDecimal hours, BigDecimal overtimeHours) {
        static Totals of(DailyWorkEntryAggregate row) {
            return new Totals(row.getHours(), row.getOvertimeHours());
        }

        static Totals plus(Totals a, Totals b) {
            return new Totals(a.hours().add(b.hours()), a.overtimeHours().add(b.overtimeHours()));
        }
    }
}
