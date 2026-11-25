package it.gestionale.api.dailyreports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.CommessaStato;
import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Notifiche operative del rapportino (#54): inviato, chiuso, mancante. Sempre via
 * {@code NotificationService.send}, mai inserimenti diretti — qui verifichiamo solo che
 * arrivino al destinatario giusto (il manager, ruolo {@code admin}), non la rubrica stessa
 * (già coperta da {@code NotificationsTest}).
 */
@AutoConfigureMockMvc
class DailyReportNotificationsTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;
    @Autowired
    CustomerRepository customers;
    @Autowired
    CommessaRepository commesse;
    @Autowired
    MissingDailyReportWatchdog watchdog;

    private UUID newTenant() {
        return TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private UUID newCommessa(UUID tenant, CommessaStato stato) {
        UUID commessaId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        TenantContext.runAs(tenant, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerId, tenant, "Cliente", null, null));
            commesse.save(new Commessa(commessaId, tenant, customerId, "Tetto", null, stato, "u"));
        }));
        return commessaId;
    }

    @Test
    void submitAndCloseNotifyTheAdmin() throws Exception {
        UUID tenant = newTenant();
        RequestPostProcessor admin = TestJwt.person(tenant, "anna", "Anna Admin", "anna@example.com", "admin");
        RequestPostProcessor capocantiere = TestJwt.person(tenant, "carlo", "Carlo Capocantiere", null,
                "capocantiere");
        mvc.perform(get("/api/me").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(capocantiere)).andExpect(status().isOk());

        UUID commessaId = newCommessa(tenant, CommessaStato.IN_CORSO);
        UUID reportId = UUID.randomUUID();
        mvc.perform(put("/api/daily-reports/" + reportId).with(capocantiere)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commessaId\":\"" + commessaId + "\",\"date\":\"2026-06-01\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/daily-reports/" + reportId + "/submit").with(capocantiere))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").with(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("system"))
                .andExpect(jsonPath("$[0].type").value("daily-reports.submitted"))
                .andExpect(jsonPath("$[0].title").value("Rapportino inviato: Tetto"));
        // Chi ha inviato il rapportino non è il destinatario di questa notifica.
        mvc.perform(get("/api/notifications").with(capocantiere)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/daily-reports/" + reportId + "/close").with(capocantiere))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").with(admin))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("daily-reports.closed"))
                .andExpect(jsonPath("$[0].title").value("Rapportino chiuso: Tetto"));
    }

    @Test
    void watchdogNotifiesTheAdminOnlyAboutActiveCommesseWithoutAReportThatDay() throws Exception {
        UUID tenantA = newTenant();
        UUID tenantB = newTenant();
        RequestPostProcessor adminA = TestJwt.person(tenantA, "anna", "Anna Admin", null, "admin");
        RequestPostProcessor adminB = TestJwt.person(tenantB, "bruno", "Bruno Admin", null, "admin");
        mvc.perform(get("/api/me").with(adminA)).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(adminB)).andExpect(status().isOk());

        LocalDate date = LocalDate.parse("2026-06-01");
        newCommessa(tenantA, CommessaStato.IN_CORSO); // senza rapportino: va notificata
        UUID withReport = newCommessa(tenantA, CommessaStato.IN_CORSO);
        newCommessa(tenantA, CommessaStato.PIANIFICATA); // non in corso: ignorata
        newCommessa(tenantB, CommessaStato.IN_CORSO); // altro tenant, senza rapportino: notificata anche lei

        RequestPostProcessor capo = TestJwt.person(tenantA, "c", "Capo", null, "capocantiere");
        mvc.perform(get("/api/me").with(capo)).andExpect(status().isOk());
        mvc.perform(put("/api/daily-reports/" + UUID.randomUUID()).with(capo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commessaId\":\"" + withReport + "\",\"date\":\"" + date + "\"}"))
                .andExpect(status().isOk());

        // Il conteggio restituito da check() non si verifica qui: gira su tutti i tenant del
        // database di test (condiviso tra le classi, vedi AbstractIntegrationTest), quindi
        // dipende anche dalle commesse create dalle altre. L'isolamento che conta è quello della
        // RLS, verificato sotto sulle notifiche dei due amministratori.
        watchdog.check(date);

        mvc.perform(get("/api/notifications").with(adminA))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("daily-reports.missing"))
                .andExpect(jsonPath("$[0].title").value("Rapportino mancante: Tetto"));
        mvc.perform(get("/api/notifications").with(adminB))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("daily-reports.missing"));

        // Rigirare sullo stesso giorno non duplica (id deterministico, send idempotente sull'id).
        watchdog.check(date);
        mvc.perform(get("/api/notifications").with(adminA)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/notifications").with(adminB)).andExpect(jsonPath("$.length()").value(1));
    }
}
