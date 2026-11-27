package it.gestionale.api.notifiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class NotificheTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;
    @Autowired
    NotificaService service;
    @Autowired
    JdbcTemplate jdbc;

    UUID tenant;
    RequestPostProcessor admin;
    RequestPostProcessor operator;
    RequestPostProcessor viewer;

    @BeforeEach
    void setUp() throws Exception {
        tenant = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        admin = JwtDiTest.person(tenant, "anna", "Anna Admin", "anna@example.com", "admin");
        operator = JwtDiTest.person(tenant, "oscar", "Oscar Operatore", "oscar@example.com", "operator");
        viewer = JwtDiTest.person(tenant, "laura", "Laura Lettrice", null, "viewer");
        // Primo accesso: registra gli utenti nella rubrica.
        for (RequestPostProcessor user : new RequestPostProcessor[] {admin, operator, viewer}) {
            mvc.perform(get("/api/me").with(user)).andExpect(status().isOk());
        }
    }

    private ResultActions announce(RequestPostProcessor as, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/announcements/" + id).with(as)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String audienceType, String audienceValue, boolean requiresAck) {
        String value = audienceValue == null ? "null" : "\"" + audienceValue + "\"";
        return """
                {"title":"Nuove regole di sicurezza","body":"Leggere con attenzione.","link":"/clienti",
                 "audience":{"type":"%s","value":%s},"requiresAck":%s,"sendEmail":false}
                """.formatted(audienceType, value, requiresAck);
    }

    @Test
    void announcementToWholeCompanyWithAcknowledgement() throws Exception {
        UUID id = UUID.randomUUID();
        announce(admin, id, body("tenant", null, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients").value(3))
                .andExpect(jsonPath("$.acknowledged").value(0));

        mvc.perform(get("/api/notifications/counts").with(viewer))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.toAcknowledge").value(1));
        mvc.perform(get("/api/notifications").with(viewer))
                .andExpect(jsonPath("$[0].title").value("Nuove regole di sicurezza"))
                .andExpect(jsonPath("$[0].senderName").value("Anna Admin"))
                .andExpect(jsonPath("$[0].requiresAck").value(true))
                .andExpect(jsonPath("$[0].readAt").doesNotExist());

        // Letta ma non confermata: resta tra le "aperte".
        mvc.perform(put("/api/notifications/" + id).with(viewer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .andExpect(jsonPath("$.readAt").exists())
                .andExpect(jsonPath("$.acknowledgedAt").doesNotExist());
        mvc.perform(get("/api/notifications?open=true").with(viewer)).andExpect(jsonPath("$.length()").value(1));

        // Presa visione, ripetuta: idempotente.
        mvc.perform(put("/api/notifications/" + id).with(viewer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledged\":true}"))
                .andExpect(jsonPath("$.acknowledgedAt").exists());
        mvc.perform(put("/api/notifications/" + id).with(viewer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledged\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/counts").with(viewer))
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.toAcknowledge").value(0));

        // Report per l'amministratore.
        mvc.perform(get("/api/announcements/" + id).with(admin))
                .andExpect(jsonPath("$.read").value(1))
                .andExpect(jsonPath("$.acknowledged").value(1));
        mvc.perform(get("/api/announcements/" + id + "/recipients").with(admin))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.userId=='laura')].acknowledgedAt").isNotEmpty())
                .andExpect(jsonPath("$[?(@.userId=='oscar')].acknowledgedAt").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void sendIsIdempotentOnId() throws Exception {
        UUID id = UUID.randomUUID();
        announce(admin, id, body("tenant", null, false)).andExpect(jsonPath("$.recipients").value(3));
        announce(admin, id, body("tenant", null, false)).andExpect(jsonPath("$.recipients").value(3));
        mvc.perform(get("/api/notifications").with(operator)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void roleAndPersonAudiences() throws Exception {
        announce(admin, UUID.randomUUID(), body("role", "operator", false)).andExpect(jsonPath("$.recipients").value(1));
        announce(admin, UUID.randomUUID(), body("user", "laura", false)).andExpect(jsonPath("$.recipients").value(1));

        mvc.perform(get("/api/notifications").with(operator)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/notifications").with(viewer)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/notifications").with(admin)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidRecipientsAreRejected() throws Exception {
        announce(admin, UUID.randomUUID(), body("user", "sconosciuto", false)).andExpect(status().isBadRequest());
        announce(admin, UUID.randomUUID(), body("role", "superuser", false)).andExpect(status().isBadRequest());
        announce(admin, UUID.randomUUID(), """
                {"title":"Link esterno","link":"https://phishing.example","audience":{"type":"tenant"}}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanSendOrSeeReports() throws Exception {
        announce(operator, UUID.randomUUID(), body("tenant", null, false)).andExpect(status().isForbidden());
        mvc.perform(get("/api/announcements").with(operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/directory/users").with(viewer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/directory/users").with(admin))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Anna Admin"));
    }

    @Test
    void usersCannotReadOrAcknowledgeOthersNotifications() throws Exception {
        UUID id = UUID.randomUUID();
        announce(admin, id, body("user", "laura", true)).andExpect(status().isOk());

        mvc.perform(get("/api/notifications/" + id).with(operator)).andExpect(status().isNotFound());
        mvc.perform(put("/api/notifications/" + id).with(operator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledged\":true}")).andExpect(status().isNotFound());

        UUID otherTenant = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        RequestPostProcessor stranger = JwtDiTest.person(otherTenant, "laura", "Laura di un'altra azienda", null, "admin");
        mvc.perform(get("/api/me").with(stranger)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").with(stranger)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/announcements/" + id).with(stranger)).andExpect(status().isNotFound());
    }

    @Test
    void acknowledgementOnlyForNotificationsThatRequireIt() throws Exception {
        UUID id = UUID.randomUUID();
        announce(admin, id, body("tenant", null, false)).andExpect(status().isOk());
        mvc.perform(put("/api/notifications/" + id).with(viewer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledged\":true}")).andExpect(status().isBadRequest());
    }

    @Test
    void newEmployeeReceivesPendingAcknowledgementsOnFirstLogin() throws Exception {
        UUID mandatory = UUID.randomUUID();
        UUID informative = UUID.randomUUID();
        UUID forViewers = UUID.randomUUID();
        announce(admin, mandatory, body("tenant", null, true)).andExpect(status().isOk());
        announce(admin, informative, body("tenant", null, false)).andExpect(status().isOk());
        announce(admin, forViewers, body("role", "viewer", true)).andExpect(status().isOk());

        RequestPostProcessor newcomer = JwtDiTest.person(tenant, "nuovo", "Nuovo Assunto", null, "operator");
        mvc.perform(get("/api/me").with(newcomer)).andExpect(status().isOk());

        mvc.perform(get("/api/notifications").with(newcomer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mandatory.toString()));
        // Accessi successivi non duplicano nulla.
        mvc.perform(get("/api/me").with(newcomer)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").with(newcomer)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void systemNotificationsFromDomainCode() throws Exception {
        int recipients = ContestoTenant.callAs(tenant, () -> service.send(NuovaNotifica.system("customers.assigned",
                "Ti è stato assegnato un cliente", "Rossi S.r.l.", "/clienti", Pubblico.user("oscar"), false, false)));
        assertThat(recipients).isEqualTo(1);
        mvc.perform(get("/api/notifications").with(operator))
                .andExpect(jsonPath("$[0].kind").value("system"))
                .andExpect(jsonPath("$[0].senderName").doesNotExist());
        // Le notifiche di sistema non compaiono tra le comunicazioni.
        mvc.perform(get("/api/announcements").with(admin)).andExpect(jsonPath("$.length()").value(0));
    }
}
