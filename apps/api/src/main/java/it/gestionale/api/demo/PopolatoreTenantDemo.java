package it.gestionale.api.demo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.StatoCommessa;
import it.gestionale.api.licenze.Funzionalita;
import it.gestionale.api.licenze.Licenza;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.notifiche.NotificaService;
import it.gestionale.api.notifiche.NuovaNotifica;
import it.gestionale.api.notifiche.Pubblico;
import it.gestionale.api.risorse.Risorsa;
import it.gestionale.api.risorse.RisorsaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.Tenant;
import it.gestionale.api.tenant.TenantRepository;
import it.gestionale.api.tenant.TipoTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Crea il tenant demo ({@code kind=demo}) con dati di esempio.
 *
 * <p>Attivo solo con {@code gestionale.demo.enabled=true}: vero di default nel profilo {@code dev},
 * falso in {@code prod} salvo configurazione esplicita ({@code DEMO_ENABLED=true}).
 * Tutte le operazioni girano nel contesto del tenant demo, quindi rispettano la RLS.
 */
@Component
@ConditionalOnProperty(name = "gestionale.demo.enabled", havingValue = "true")
public class PopolatoreTenantDemo implements ApplicationRunner {

    /** Id fisso del tenant demo, così il frontend e i test possono riferirlo. */
    public static final UUID DEMO_TENANT_ID = UUID.fromString("00000000-0000-7000-8000-00000000de00");

    private static final Logger log = LoggerFactory.getLogger(PopolatoreTenantDemo.class);

    private final TenantRepository tenants;
    private final LicenzaRepository licenses;
    private final ClienteRepository customers;
    private final RisorsaRepository resources;
    private final CommessaRepository commesse;
    private final NotificaService notifications;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PopolatoreTenantDemo(TenantRepository tenants, LicenzaRepository licenses, ClienteRepository customers,
                            RisorsaRepository resources, CommessaRepository commesse,
                            NotificaService notifications, JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.tenants = tenants;
        this.licenses = licenses;
        this.customers = customers;
        this.resources = resources;
        this.commesse = commesse;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        ContestoTenant.runAs(DEMO_TENANT_ID, () -> tx.executeWithoutResult(status -> {
            if (tenants.existsById(DEMO_TENANT_ID)) {
                log.info("Tenant demo già presente");
                return;
            }
            tenants.save(new Tenant(DEMO_TENANT_ID, "Azienda Demo", "demo", TipoTenant.DEMO));
            insertSampleData();
            log.info("Tenant demo creato");
        }));
    }

    /** Cancella i dati del tenant demo e ricarica quelli iniziali. */
    public void reset() {
        ContestoTenant.runAs(DEMO_TENANT_ID, () -> tx.executeWithoutResult(status -> {
            if (!tenants.existsById(DEMO_TENANT_ID)) {
                tenants.save(new Tenant(DEMO_TENANT_ID, "Azienda Demo", "demo", TipoTenant.DEMO));
            }
            jdbc.update("DELETE FROM notifications WHERE tenant_id = ?", DEMO_TENANT_ID);
            // commesse.customer_id e resources.team_lead_id sono chiavi esterne vere ma non
            // relazioni JPA mappate: Hibernate non sa che vanno sciolte prima delle righe che
            // referenziano (lo si è visto rotto in pratica). DELETE/UPDATE diretti invece delle
            // repository: eseguono subito, nell'ordine scritto, senza dipendere da come il flush
            // ordinerebbe entità che non si conoscono a vicenda.
            jdbc.update("DELETE FROM commesse WHERE tenant_id = ?", DEMO_TENANT_ID);
            jdbc.update("UPDATE resources SET team_lead_id = NULL WHERE tenant_id = ?", DEMO_TENANT_ID);
            customers.deleteByTenantId(DEMO_TENANT_ID);
            resources.deleteByTenantId(DEMO_TENANT_ID);
            licenses.deleteByTenantId(DEMO_TENANT_ID);
            customers.flush();
            insertSampleData();
            log.info("Tenant demo ripristinato");
        }));
    }

    private void insertSampleData() {
        Instant now = clock.instant();
        licenses.save(new Licenza(UUID.randomUUID(), DEMO_TENANT_ID, "demo", 5,
                Set.of(Funzionalita.AI_ASSISTANT), 50, now, null));
        // createdBy null: i dati di esempio non li ha scritti un utente. Gli id sono generati qui
        // (come farebbe il client vero) e riusati subito sotto: non serve un save intermedio solo
        // per rileggerli, un unico saveAll per tabella basta.
        UUID rossiId = UUID.randomUUID();
        UUID capoId = UUID.randomUUID();
        customers.saveAll(List.of(
                new Cliente(rossiId, DEMO_TENANT_ID, "Rossi S.r.l.", "info@rossi.example", null),
                new Cliente(UUID.randomUUID(), DEMO_TENANT_ID, "Bianchi & Figli", "amministrazione@bianchi.example", null),
                new Cliente(UUID.randomUUID(), DEMO_TENANT_ID, "Verdi S.p.A.", null, null)));
        resources.saveAll(List.of(
                new Risorsa(capoId, DEMO_TENANT_ID, "Luca", "Ferrari", null, null, null),
                new Risorsa(UUID.randomUUID(), DEMO_TENANT_ID, "Marco", "Bruno", null, capoId, null)));
        // Scrive subito clienti e risorse: la commessa (FK vera verso customers) li referenzia, e le
        // repository condividono la stessa sessione JPA, quindi un solo flush basta.
        commesse.flush();
        commesse.save(new Commessa(UUID.randomUUID(), DEMO_TENANT_ID, rossiId,
                "Ristrutturazione tetto - Via Roma 12", "Via Roma 12, Tivoli (RM)", StatoCommessa.IN_CORSO, null));
        // Comunicazione con presa visione a tutta l'azienda: chi accede dopo la riceve comunque.
        notifications.send(new NuovaNotifica(UUID.randomUUID(), NuovaNotifica.ANNOUNCEMENT, "announcement",
                "Benvenuto in Civil Beefy",
                "Questa è una comunicazione di prova dell'ambiente demo. Leggila e conferma la presa visione.",
                "/clienti", Pubblico.tenant(), true, true, null, "Amministrazione Demo"));
    }
}
