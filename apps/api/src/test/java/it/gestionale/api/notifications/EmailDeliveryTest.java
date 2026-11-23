package it.gestionale.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "gestionale.notifications.email.enabled=true",
        "gestionale.notifications.email.app-url=https://gestionale.azienda.example",
        "gestionale.notifications.email.poll-interval=1h",
        "gestionale.notifications.email.max-attempts=2"})
class EmailDeliveryTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;
    @Autowired
    EmailDispatcher dispatcher;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JavaMailSender mailSender;

    UUID tenant;
    RequestPostProcessor admin;

    @BeforeEach
    void setUp() throws Exception {
        reset(mailSender);
        // Svuota la coda lasciata da altri test (il dispatcher serve tutti i tenant).
        while (dispatcher.dispatch(500) > 0) {
            // niente
        }
        reset(mailSender);
        tenant = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        admin = TestJwt.person(tenant, "anna", "Anna Admin", "anna@example.com", "admin");
        mvc.perform(get("/api/me").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(TestJwt.person(tenant, "oscar", "Oscar", "oscar@example.com", "operator")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").with(TestJwt.person(tenant, "senza-email", "Senza Email", null, "viewer")))
                .andExpect(status().isOk());
    }

    private void announce(UUID id, boolean sendEmail) throws Exception {
        mvc.perform(put("/api/announcements/" + id).with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Chiusura per inventario","body":"Venerdì l'ufficio resta chiuso.",
                         "audience":{"type":"tenant"},"requiresAck":true,"sendEmail":%s}
                        """.formatted(sendEmail)))
                .andExpect(status().isOk());
    }

    @Test
    void queuesOneEmailPerRecipientWithAddressAndSendsThem() throws Exception {
        UUID id = UUID.randomUUID();
        announce(id, true);

        assertThat(dispatcher.dispatch(50)).isEqualTo(2);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(sent.capture());
        SimpleMailMessage message = sent.getAllValues().getFirst();
        assertThat(message.getSubject()).startsWith("[Tenant base] Da confermare: Chiusura per inventario");
        assertThat(message.getText())
                .contains("Venerdì l'ufficio resta chiuso.")
                .contains("https://gestionale.azienda.example/notifiche/" + id);
        assertThat(sent.getAllValues()).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder("anna@example.com", "oscar@example.com");

        mvc.perform(get("/api/announcements/" + id).with(admin)).andExpect(jsonPath("$.emailsSent").value(2));
        mvc.perform(get("/api/announcements/" + id + "/recipients").with(admin))
                .andExpect(jsonPath("$[?(@.userId=='oscar')].emailStatus").value(org.hamcrest.Matchers.contains("sent")));
    }

    @Test
    void noEmailWhenNotRequested() throws Exception {
        announce(UUID.randomUUID(), false);
        assertThat(dispatcher.dispatch(50)).isZero();
    }

    @Test
    void failedDeliveriesAreRetriedThenMarkedFailed() throws Exception {
        doThrow(new MailSendException("SMTP non raggiungibile")).when(mailSender).send(any(SimpleMailMessage.class));
        UUID id = UUID.randomUUID();
        announce(id, true);

        assertThat(dispatcher.dispatch(50)).isZero();
        List<Map<String, Object>> rows = TenantContext.callAs(tenant, () -> jdbc.queryForList(
                "SELECT status, attempts FROM email_deliveries WHERE tenant_id = ? AND notification_id = ?", tenant, id));
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("status")).isEqualTo("pending");
            assertThat(r.get("attempts")).isEqualTo(1);
        });

        // Secondo tentativo (anticipato): con max-attempts=2 la consegna diventa "failed".
        TenantContext.runAs(tenant, () -> jdbc.update(
                "UPDATE email_deliveries SET next_attempt_at = now() WHERE tenant_id = ? AND notification_id = ?", tenant, id));
        dispatcher.dispatch(50);
        mvc.perform(get("/api/announcements/" + id).with(admin)).andExpect(jsonPath("$.emailsFailed").value(2));
    }

    @Test
    void emailQueueIsNotVisibleWithoutTenantOrWorkerScope() {
        Integer visible = jdbc.queryForObject("SELECT count(*) FROM email_deliveries", Integer.class);
        assertThat(visible).isZero();
    }
}
