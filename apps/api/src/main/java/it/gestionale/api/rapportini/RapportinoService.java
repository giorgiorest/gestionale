package it.gestionale.api.rapportini;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.accesso.Ruolo;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.notifiche.NotificaService;
import it.gestionale.api.notifiche.NuovaNotifica;
import it.gestionale.api.notifiche.Pubblico;
import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RapportinoService {

    private final RapportinoRepository reports;
    private final CommessaRepository commesse;
    private final RigaOreService workEntries;
    private final NotificaService notifications;
    private final Clock clock;

    public RapportinoService(RapportinoRepository reports, CommessaRepository commesse,
                              RigaOreService workEntries, NotificaService notifications, Clock clock) {
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
    public List<RapportinoDto> listByCommessa(UUID commessaId, LocalDate date) {
        UUID tenantId = ContestoTenant.require();
        List<Rapportino> found = date == null
                ? reports.findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByDateDesc(commessaId, tenantId)
                : reports.findByCommessaIdAndTenantIdAndDateAndDeletedAtIsNull(commessaId, tenantId, date);
        return found.stream().map(RapportinoDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<RapportinoDto> get(UUID id) {
        return reports.findByIdAndTenantId(id, ContestoTenant.require())
                .filter(r -> !r.isDeleted())
                .map(RapportinoDto::from);
    }

    /**
     * Crea o aggiorna la testata con l'id indicato (generato dal client), sempre come DRAFT.
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Un rapportino cancellato logicamente torna attivo (vedi {@link Rapportino#update}).
     */
    @Transactional
    public RapportinoDto upsert(UUID id, RapportinoDto.Input input, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        UUID commessaId = input.commessaId();
        Optional<Rapportino> existing = reports.findByIdAndTenantId(id, tenantId);
        // Un rapportino non più DRAFT non è modificabile da questo endpoint ordinario (#51): solo
        // submit/close possono farlo cambiare stato. Un id nuovo è sempre DRAFT, niente da
        // controllare.
        existing.ifPresent(Rapportino::requireDraft);
        // Come per commesse.customer_id: si valida solo alla creazione o quando il riferimento
        // cambia davvero, così un record già collegato resta aggiornabile anche se la commessa è
        // stata cancellata logicamente nel frattempo per altri motivi.
        if (existing.map(Rapportino::getCommessaId).map(c -> !c.equals(commessaId)).orElse(true)) {
            requireValidCommessa(commessaId, tenantId);
        }
        Rapportino report = existing
                .map(r -> {
                    r.update(commessaId, input.date(), input.weather(), input.notes(), actorUserId, clock.instant());
                    return r;
                })
                .orElseGet(() -> reports.save(new Rapportino(id, tenantId, commessaId, input.date(), input.weather(),
                        input.notes(), actorUserId)));
        reports.flush();
        return RapportinoDto.from(report);
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
        reports.findByIdAndTenantId(id, ContestoTenant.require()).ifPresent(report -> {
            if (!report.isDeleted()) {
                report.requireDraft();
            }
            report.markDeleted(actorUserId, clock.instant());
        });
        reports.flush();
    }

    /**
     * DRAFT → SUBMITTED. Non idempotente (vedi {@link Rapportino#submit}): un secondo invio
     * fallisce con 409, invece di essere un no-op silenzioso — è la base del test di concorrenza
     * della storia #51. Notifica il manager (#54): deve verificarlo e chiuderlo.
     */
    @Transactional
    public RapportinoDto submit(UUID id, String actorUserId) {
        Rapportino report = requireActive(id);
        report.submit(actorUserId, clock.instant());
        reports.flush();
        notifyManager("daily-reports.submitted", "Rapportino inviato", report,
                "è stato inviato ed è pronto per la verifica");
        return RapportinoDto.from(report);
    }

    /**
     * SUBMITTED → CLOSED, con lo snapshot del costo manodopera delle righe di ore nella stessa
     * transazione: se la transizione fallisce (stato sbagliato, o conflitto di versione al
     * flush), nessuno snapshot viene scritto. Notifica il manager (#54).
     */
    @Transactional
    public RapportinoDto close(UUID id, String actorUserId) {
        Rapportino report = requireActive(id);
        report.close(actorUserId, clock.instant());
        workEntries.snapshotCostsForClose(report);
        reports.flush();
        notifyManager("daily-reports.closed", "Rapportino chiuso", report, "è stato chiuso");
        return RapportinoDto.from(report);
    }

    /**
     * Avvisa chi gestisce l'azienda ({@code admin}, #54): è anche il ruolo di manager/proprietà
     * (vedi {@code Ruolo.ADMIN}). Sempre con {@link NotificaService#send}, mai un insert
     * diretto.
     */
    private void notifyManager(String type, String title, Rapportino report, String verb) {
        String commessaName = commesse.findByIdAndTenantId(report.getCommessaId(), ContestoTenant.require())
                .map(Commessa::getNome)
                .orElse("commessa " + report.getCommessaId());
        notifications.send(NuovaNotifica.system(type, title + ": " + commessaName,
                "Il rapportino del " + report.getDate() + " per la commessa \"" + commessaName + "\" " + verb + ".",
                "/commesse", Pubblico.role(Ruolo.ADMIN.code()), false, false));
    }

    private Rapportino requireActive(UUID id) {
        return reports.findByIdAndTenantId(id, ContestoTenant.require())
                .filter(r -> !r.isDeleted())
                .orElseThrow(RapportinoNonTrovatoException::new);
    }

    /** Difesa in profondità: la commessa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidCommessa(UUID commessaId, UUID tenantId) {
        boolean active = commesse.findByIdAndTenantId(commessaId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new RiferimentoCommessaSconosciutoException("Commessa non valida: " + commessaId);
        }
    }
}
