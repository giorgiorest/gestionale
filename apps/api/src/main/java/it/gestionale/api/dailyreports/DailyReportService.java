package it.gestionale.api.dailyreports;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.auth.Role;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.notifications.Audience;
import it.gestionale.api.notifications.NewNotification;
import it.gestionale.api.notifications.NotificationService;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.workentries.WorkEntryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReportService {

    private final DailyReportRepository reports;
    private final CommessaRepository commesse;
    private final WorkEntryService workEntries;
    private final NotificationService notifications;
    private final Clock clock;

    public DailyReportService(DailyReportRepository reports, CommessaRepository commesse,
                              WorkEntryService workEntries, NotificationService notifications, Clock clock) {
        this.reports = reports;
        this.commesse = commesse;
        this.workEntries = workEntries;
        this.notifications = notifications;
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
        // Un rapportino non più DRAFT non è modificabile da questo endpoint ordinario (#51): solo
        // submit/close possono farlo cambiare stato. Un id nuovo è sempre DRAFT, niente da
        // controllare.
        existing.ifPresent(DailyReport::requireDraft);
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
     * Un rapportino non più DRAFT non si può cancellare da qui (#51); un rapportino già
     * cancellato resta sempre DRAFT (si può cancellare solo da quello stato), quindi il controllo
     * si salta per non rompere la re-invio idempotente dalla outbox.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        reports.findByIdAndTenantId(id, TenantContext.require()).ifPresent(report -> {
            if (!report.isDeleted()) {
                report.requireDraft();
            }
            report.markDeleted(actorUserId, clock.instant());
        });
        reports.flush();
    }

    /**
     * DRAFT → SUBMITTED. Non idempotente (vedi {@link DailyReport#submit}): un secondo invio
     * fallisce con 409, invece di essere un no-op silenzioso — è la base del test di concorrenza
     * della storia #51. Notifica il manager (#54): deve verificarlo e chiuderlo.
     */
    @Transactional
    public DailyReportDto submit(UUID id, String actorUserId) {
        DailyReport report = requireActive(id);
        report.submit(actorUserId, clock.instant());
        reports.flush();
        notifyManager("daily-reports.submitted", "Rapportino inviato", report,
                "è stato inviato ed è pronto per la verifica");
        return DailyReportDto.from(report);
    }

    /**
     * SUBMITTED → CLOSED, con lo snapshot del costo manodopera delle righe di ore nella stessa
     * transazione: se la transizione fallisce (stato sbagliato, o conflitto di versione al
     * flush), nessuno snapshot viene scritto. Notifica il manager (#54).
     */
    @Transactional
    public DailyReportDto close(UUID id, String actorUserId) {
        DailyReport report = requireActive(id);
        report.close(actorUserId, clock.instant());
        workEntries.snapshotCostsForClose(report);
        reports.flush();
        notifyManager("daily-reports.closed", "Rapportino chiuso", report, "è stato chiuso");
        return DailyReportDto.from(report);
    }

    /**
     * Avvisa chi gestisce l'azienda ({@code admin}, #54): è anche il ruolo di manager/proprietà
     * (vedi {@code Role.ADMIN}). Sempre con {@link NotificationService#send}, mai un insert
     * diretto.
     */
    private void notifyManager(String type, String title, DailyReport report, String verb) {
        String commessaName = commesse.findByIdAndTenantId(report.getCommessaId(), TenantContext.require())
                .map(Commessa::getNome)
                .orElse("commessa " + report.getCommessaId());
        notifications.send(NewNotification.system(type, title + ": " + commessaName,
                "Il rapportino del " + report.getDate() + " per la commessa \"" + commessaName + "\" " + verb + ".",
                "/commesse", Audience.role(Role.ADMIN.code()), false, false));
    }

    private DailyReport requireActive(UUID id) {
        return reports.findByIdAndTenantId(id, TenantContext.require())
                .filter(r -> !r.isDeleted())
                .orElseThrow(DailyReportNotFoundException::new);
    }

    /** Difesa in profondità: la commessa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidCommessa(UUID commessaId, UUID tenantId) {
        boolean active = commesse.findByIdAndTenantId(commessaId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new UnknownCommessaReferenceException("Commessa non valida: " + commessaId);
        }
    }
}
