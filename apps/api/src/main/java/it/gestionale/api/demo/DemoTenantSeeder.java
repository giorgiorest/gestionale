package it.gestionale.api.demo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.licensing.Features;
import it.gestionale.api.licensing.License;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.notifications.Audience;
import it.gestionale.api.notifications.NewNotification;
import it.gestionale.api.notifications.NotificationService;
import it.gestionale.api.tenancy.Tenant;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantKind;
import it.gestionale.api.tenancy.TenantRepository;
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
public class DemoTenantSeeder implements ApplicationRunner {

    /** Id fisso del tenant demo, così il frontend e i test possono riferirlo. */
    public static final UUID DEMO_TENANT_ID = UUID.fromString("00000000-0000-7000-8000-00000000de00");

    private static final Logger log = LoggerFactory.getLogger(DemoTenantSeeder.class);

    private final TenantRepository tenants;
    private final LicenseRepository licenses;
    private final CustomerRepository customers;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DemoTenantSeeder(TenantRepository tenants, LicenseRepository licenses, CustomerRepository customers,
                            NotificationService notifications, JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.tenants = tenants;
        this.licenses = licenses;
        this.customers = customers;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.runAs(DEMO_TENANT_ID, () -> tx.executeWithoutResult(status -> {
            if (tenants.existsById(DEMO_TENANT_ID)) {
                log.info("Tenant demo già presente");
                return;
            }
            tenants.save(new Tenant(DEMO_TENANT_ID, "Azienda Demo", "demo", TenantKind.DEMO));
            insertSampleData();
            log.info("Tenant demo creato");
        }));
    }

    /** Cancella i dati del tenant demo e ricarica quelli iniziali. */
    public void reset() {
        TenantContext.runAs(DEMO_TENANT_ID, () -> tx.executeWithoutResult(status -> {
            if (!tenants.existsById(DEMO_TENANT_ID)) {
                tenants.save(new Tenant(DEMO_TENANT_ID, "Azienda Demo", "demo", TenantKind.DEMO));
            }
            jdbc.update("DELETE FROM notifications WHERE tenant_id = ?", DEMO_TENANT_ID);
            customers.deleteByTenantId(DEMO_TENANT_ID);
            licenses.deleteByTenantId(DEMO_TENANT_ID);
            customers.flush();
            licenses.flush();
            insertSampleData();
            log.info("Tenant demo ripristinato");
        }));
    }

    private void insertSampleData() {
        Instant now = clock.instant();
        licenses.save(new License(UUID.randomUUID(), DEMO_TENANT_ID, "demo", 5,
                Set.of(Features.AI_ASSISTANT), 50, now, null));
        customers.saveAll(List.of(
                new Customer(UUID.randomUUID(), DEMO_TENANT_ID, "Rossi S.r.l.", "info@rossi.example"),
                new Customer(UUID.randomUUID(), DEMO_TENANT_ID, "Bianchi & Figli", "amministrazione@bianchi.example"),
                new Customer(UUID.randomUUID(), DEMO_TENANT_ID, "Verdi S.p.A.", null)));
        // Scrive subito tenant, licenza e clienti: la notifica (JDBC) li referenzia.
        customers.flush();
        // Comunicazione con presa visione a tutta l'azienda: chi accede dopo la riceve comunque.
        notifications.send(new NewNotification(UUID.randomUUID(), NewNotification.ANNOUNCEMENT, "announcement",
                "Benvenuto nel gestionale",
                "Questa è una comunicazione di prova dell'ambiente demo. Leggila e conferma la presa visione.",
                "/clienti", Audience.tenant(), true, true, null, "Amministrazione Demo"));
    }
}
