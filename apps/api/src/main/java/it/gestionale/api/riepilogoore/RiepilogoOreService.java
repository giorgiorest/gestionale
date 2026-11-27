package it.gestionale.api.riepilogoore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cruscotto ore (#52): tre viste derivate dalla stessa aggregazione per giorno/risorsa/lavorazione. */
@Service
public class RiepilogoOreService {

    private final RiepilogoOreRepository repository;

    public RiepilogoOreService(RiepilogoOreRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RiepilogoOreDto report(UUID commessaId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IntervalloRiepilogoOreNonValidoException(
                    "L'intervallo non è valido: \"a\" (" + to + ") è prima di \"da\" (" + from + ")");
        }
        List<AggregatoOreGiornaliere> rows = repository.aggregateByDateResourceAndActivity(commessaId, from, to,
                ContestoTenant.require());

        Map<LocalDate, Totals> byWeek = new TreeMap<>();
        Map<MeseRisorsa, Totals> byResourceMonth = new HashMap<>();
        Map<UUID, Totals> byActivity = new HashMap<>();
        for (AggregatoOreGiornaliere row : rows) {
            byWeek.merge(mondayOf(row.getDate()), Totals.of(row), Totals::plus);
            byResourceMonth.merge(new MeseRisorsa(row.getDate().withDayOfMonth(1), row.getResourceId()),
                    Totals.of(row), Totals::plus);
            byActivity.merge(row.getWorkActivityId(), Totals.of(row), Totals::plus);
        }

        List<OreSettimanaliDto> weekly = byWeek.entrySet().stream()
                .map(e -> new OreSettimanaliDto(e.getKey(), e.getValue().hours(), e.getValue().overtimeHours()))
                .toList();
        List<OreRisorsaDto> byResource = byResourceMonth.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<MeseRisorsa, Totals> e) -> e.getKey().month())
                        .thenComparing(e -> e.getKey().resourceId()))
                .map(e -> new OreRisorsaDto(e.getKey().resourceId(), e.getKey().month(), e.getValue().hours(),
                        e.getValue().overtimeHours()))
                .toList();
        List<OreLavorazioneDto> byWorkActivity = byActivity.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new OreLavorazioneDto(e.getKey(), e.getValue().hours(), e.getValue().overtimeHours()))
                .toList();

        return new RiepilogoOreDto(commessaId, from, to, weekly, byResource, byWorkActivity);
    }

    /** Lunedì della settimana ISO (da lunedì a domenica) che contiene la data. */
    private static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private record MeseRisorsa(LocalDate month, UUID resourceId) {
    }

    private record Totals(BigDecimal hours, BigDecimal overtimeHours) {
        static Totals of(AggregatoOreGiornaliere row) {
            return new Totals(row.getHours(), row.getOvertimeHours());
        }

        static Totals plus(Totals a, Totals b) {
            return new Totals(a.hours().add(b.hours()), a.overtimeHours().add(b.overtimeHours()));
        }
    }
}
