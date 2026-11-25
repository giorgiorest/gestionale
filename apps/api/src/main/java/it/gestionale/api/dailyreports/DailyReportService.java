package it.gestionale.api.dailyreports;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReportService {

    private final DailyReportRepository reports;
    private final CommessaRepository commesse;
    private final Clock clock;

    public DailyReportService(DailyReportRepository reports, CommessaRepository commesse, Clock clock) {
        this.reports = reports;
        this.commesse = commesse;
        this.clock = clock;
    }

    /**
     * Rapportini della commessa, più recenti prima; con {@code date} filtra sul singolo giorno
     * (al più uno, per il vincolo di unicità). I record cancellati non compaiono.
     */
    @Transactional(readOnly = true)
    public List<DailyReportDto> listByCommessa(UUID commessaId, LocalDate date) {
        UUID tenantId = TenantContext.require();
        List<DailyReport> found = date == null
                ? reports.findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByDateDesc(commessaId, tenantId)
                : reports.findByCommessaIdAndTenantIdAndDateAndDeletedAtIsNull(commessaId, tenantId, date);
        return found.stream().map(DailyReportDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<DailyReportDto> get(UUID id) {
        return reports.findByIdAndTenantId(id, TenantContext.require())
                .filter(r -> !r.isDeleted())
                .map(DailyReportDto::from);
    }

    /**
     * Crea o aggiorna la testata con l'id indicato (generato dal client), sempre come DRAFT.
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Un rapportino cancellato logicamente torna attivo (vedi {@link DailyReport#update}).
     */
    @Transactional
    public DailyReportDto upsert(UUID id, DailyReportDto.Input input, String actorUserId) {
        UUID tenantId = TenantContext.require();
        UUID commessaId = input.commessaId();
        Optional<DailyReport> existing = reports.findByIdAndTenantId(id, tenantId);
        // Come per commesse.customer_id: si valida solo alla creazione o quando il riferimento
        // cambia davvero, così un record già collegato resta aggiornabile anche se la commessa è
        // stata cancellata logicamente nel frattempo per altri motivi.
        if (existing.map(DailyReport::getCommessaId).map(c -> !c.equals(commessaId)).orElse(true)) {
            requireValidCommessa(commessaId, tenantId);
        }
        DailyReport report = existing
                .map(r -> {
                    r.update(commessaId, input.date(), input.weather(), input.notes(), actorUserId, clock.instant());
                    return r;
                })
                .orElseGet(() -> reports.save(new DailyReport(id, tenantId, commessaId, input.date(), input.weather(),
                        input.notes(), actorUserId)));
        reports.flush();
        return DailyReportDto.from(report);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        reports.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(report -> report.markDeleted(actorUserId, clock.instant()));
        reports.flush();
    }

    /** Difesa in profondità: la commessa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidCommessa(UUID commessaId, UUID tenantId) {
        boolean active = commesse.findByIdAndTenantId(commessaId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new UnknownCommessaReferenceException("Commessa non valida: " + commessaId);
        }
    }
}
