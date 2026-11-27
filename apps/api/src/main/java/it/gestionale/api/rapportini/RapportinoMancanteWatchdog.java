package it.gestionale.api.rapportini;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.accesso.Ruolo;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.StatoCommessa;
import it.gestionale.api.notifiche.NotificaService;
import it.gestionale.api.notifiche.NuovaNotifica;
import it.gestionale.api.notifiche.Pubblico;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.ContestoWorker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Notifica al manager, una volta al giorno, le commesse in corso senza un rapportino per la data
 * indicata (#54): un controllo periodico, non su richiesta, perché il caso da avvisare è
 * l'assenza di un'azione (nessun evento la fa scattare).
 *
 * <p>Gira per tutti i tenant con {@code app.worker = 'daily-report-watchdog'} (come
 * {@code InviatoreEmail}): la policy dedicata (V15) serve solo a elencare le aziende attive da
 * {@code tenants}. Le tabelle di dominio restano filtrate per tenant: ogni azienda viene
 * controllata nel suo contesto ({@link ContestoTenant}), mai con la RLS disattivata.
 */
@Component
public class RapportinoMancanteWatchdog {

    static final String WORKER = "daily-report-watchdog";

    private final JdbcTemplate jdbc;
    private final CommessaRepository commesse;
    private final RapportinoRepository reports;
    private final NotificaService notifications;
    private final Clock clock;

    public RapportinoMancanteWatchdog(JdbcTemplate jdbc, CommessaRepository commesse, RapportinoRepository reports,
                                      NotificaService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.commesse = commesse;
        this.reports = reports;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Fine giornata lavorativa: le commesse hanno avuto tutto il giorno per registrare il rapportino. */
    @Scheduled(cron = "${gestionale.notifications.missing-daily-report.cron:0 0 19 * * *}")
    public void run() {
        check(LocalDate.now(clock));
    }

    /** @return quante notifiche di rapportino mancante sono state inviate, in tutti i tenant */
    public int check(LocalDate date) {
        List<UUID> tenantIds = ContestoWorker.callAs(WORKER,
                () -> jdbc.queryForList("SELECT id FROM tenants WHERE status = 'active'", UUID.class));
        int notified = 0;
        for (UUID tenantId : tenantIds) {
            notified += ContestoTenant.callAs(tenantId, () -> checkTenant(date));
        }
        return notified;
    }

    private int checkTenant(LocalDate date) {
        UUID tenantId = ContestoTenant.require();
        int notified = 0;
        for (Commessa commessa : commesse.findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(tenantId)) {
            if (commessa.getStato() != StatoCommessa.IN_CORSO) {
                continue;
            }
            boolean hasReport = !reports
                    .findByCommessaIdAndTenantIdAndDateAndDeletedAtIsNull(commessa.getId(), tenantId, date).isEmpty();
            if (!hasReport) {
                notifications.send(missingReportNotification(commessa, date));
                notified++;
            }
        }
        return notified;
    }

    /**
     * Id deterministico (commessa + data): a differenza di {@code submit}/{@code close}, questo
     * controllo può rigirare più volte sullo stesso giorno (riavvio, orario diverso), e
     * {@link NotificaService#send} è già idempotente sull'id.
     */
    private static NuovaNotifica missingReportNotification(Commessa commessa, LocalDate date) {
        UUID id = UUID.nameUUIDFromBytes(
                ("daily-reports.missing:" + commessa.getId() + ":" + date).getBytes(StandardCharsets.UTF_8));
        return new NuovaNotifica(id, NuovaNotifica.SYSTEM, "daily-reports.missing",
                "Rapportino mancante: " + commessa.getNome(),
                "Nessun rapportino registrato per la commessa \"" + commessa.getNome() + "\" in data " + date + ".",
                "/commesse", Pubblico.role(Ruolo.ADMIN.code()), false, false, null, null);
    }
}
