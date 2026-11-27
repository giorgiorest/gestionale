package it.gestionale.api.rapportini;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.StatoCommessa;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Notifiche operative del rapportino (#54): inviato, chiuso, mancante. Sempre via
 * {@code NotificaService.send}, mai inserimenti diretti — qui verifichiamo solo che
 * arrivino al destinatario giusto (il manager, ruolo {@code admin}), non la rubrica stessa
 * (già coperta da {@code NotificheTest}).
 */
@AutoConfigureMockMvc
class RapportinoNotificheTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;
    @Autowired
    ClienteRepository customers;
    @Autowired
    CommessaRepository commesse;
    @Autowired
    RapportinoMancanteWatchdog watchdog;

    private UUID newTenant() {
        return DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private UUID newCommessa(UUID tenant, StatoCommessa stato) {
        UUID commessaId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ContestoTenant.runAs(tenant, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerId, tenant, "Cliente", null, null));
            commesse.save(new Commessa(commessaId, tenant, customerId, "Tetto", null, stato, "u"));
        }));
        return commessaId;
    }

    @Test
    void submitAndCloseNotifyTheAdmin() throws Exception {
        UUID tenant = newTenant();
        RequestPostProcessor admin = JwtDiTest.person(tenant, "anna", "Anna Admin", "anna@example.com", "admin");
        RequestPostProcessor capocantiere = JwtDiTest.person(tenant, "carlo", "Carlo Capocantiere", null,
                "capocantiere");
        mvc.perform(get("/api/me").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(capocantiere)).andExpect(status().isOk());

        UUID commessaId = newCommessa(tenant, StatoCommessa.IN_CORSO);
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
        RequestPostProcessor adminA = JwtDiTest.person(tenantA, "anna", "Anna Admin", null, "admin");
        RequestPostProcessor adminB = JwtDiTest.person(tenantB, "bruno", "Bruno Admin", null, "admin");
        mvc.perform(get("/api/me").with(adminA)).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(adminB)).andExpect(status().isOk());

        LocalDate date = LocalDate.parse("2026-06-01");
        newCommessa(tenantA, StatoCommessa.IN_CORSO); // senza rapportino: va notificata
        UUID withReport = newCommessa(tenantA, StatoCommessa.IN_CORSO);
        newCommessa(tenantA, StatoCommessa.PIANIFICATA); // non in corso: ignorata
        newCommessa(tenantB, StatoCommessa.IN_CORSO); // altro tenant, senza rapportino: notificata anche lei

        RequestPostProcessor capo = JwtDiTest.person(tenantA, "c", "Capo", null, "capocantiere");
        mvc.perform(get("/api/me").with(capo)).andExpect(status().isOk());
        mvc.perform(put("/api/daily-reports/" + UUID.randomUUID()).with(capo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commessaId\":\"" + withReport + "\",\"date\":\"" + date + "\"}"))
                .andExpect(status().isOk());

        // Il conteggio restituito da check() non si verifica qui: gira su tutti i tenant del
        // database di test (condiviso tra le classi, vedi TestIntegrazioneBase), quindi
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
