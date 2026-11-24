package it.gestionale.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.platform.identity.IdentityAdmin;
import it.gestionale.api.platform.identity.IdentityAdminException;
import it.gestionale.api.platform.identity.NewTenantUser;
import it.gestionale.api.platform.identity.TenantUser;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/** Console di piattaforma: chi può entrare, cosa vede, cosa registra. */
@AutoConfigureMockMvc
@Import(PlatformApiTest.FakeIdentityConfig.class)
class PlatformApiTest extends AbstractIntegrationTest {

    static final String ISSUER = "http://localhost:0/realms/piattaforma";

    @Autowired
    MockMvc mvc;
    @Autowired
    PlatformProperties properties;
    @Autowired
    FakeIdentity identity;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;
    @Autowired
    JdbcTemplate jdbc;

    UUID existingTenant;

    @BeforeEach
    void setUp() {
        identity.users.clear();
        existingTenant = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    JwtRequestPostProcessor admin() {
        return platformToken(ISSUER, "platform-admin");
    }

    JwtRequestPostProcessor platformToken(String issuer, String... roles) {
        return jwt().jwt(j -> j.issuer(issuer).subject("adm-1").claim("name", "Ada Admin")
                        .claim("roles", List.of(roles)))
                .authorities(PlatformSecurityConfig.authorities(properties.jwt()));
    }

    String newTenantJson(UUID id, String slug, int maxUsers) {
        return """
                {"id":"%s","name":"Rossi S.r.l.","slug":"%s",
                 "license":{"plan":"standard","maxUsers":%d,"features":["aiAssistant"],"aiQuotaMonthly":100,
                            "validUntil":"2099-12-31T00:00:00Z"}}
                """.formatted(id, slug, maxUsers);
    }

    @Test
    void withoutTokenTheConsoleIsClosed() throws Exception {
        mvc.perform(get("/api/platform/tenants")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeesOfACompanyCannotUseTheConsole() throws Exception {
        // Token del realm delle aziende, anche di un amministratore dell'azienda.
        mvc.perform(get("/api/platform/tenants").with(TestJwt.user(existingTenant, "admin")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/tenants").with(TestJwt.user(existingTenant, "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(newTenantJson(UUID.randomUUID(), "intruso", 5)))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformRoleFromAnotherIssuerOrWithoutRoleIsRejected() throws Exception {
        mvc.perform(get("/api/platform/tenants").with(platformToken("http://localhost:0/realms/gestionale", "platform-admin")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/tenants").with(platformToken(ISSUER, "admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformTokenDoesNotOpenCompanyData() throws Exception {
        // Nessun tenant nel token degli amministratori: le API delle aziende restano chiuse.
        mvc.perform(get("/api/customers").with(admin())).andExpect(status().isForbidden());
    }

    @Test
    void createListAndUpdateACompanyWithItsLicense() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/platform/tenants").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(newTenantJson(id, "rossi-" + id.toString().substring(0, 8), 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.license.maxUsers").value(5))
                .andExpect(jsonPath("$.license.features[0]").value("aiAssistant"));

        mvc.perform(get("/api/platform/tenants").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].name".formatted(id)).value("Rossi S.r.l."))
                .andExpect(jsonPath("$[?(@.id == '%s')].slug".formatted(existingTenant)).exists());

        mvc.perform(put("/api/platform/tenants/{id}/license", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"pro\",\"maxUsers\":20,\"features\":[],\"aiQuotaMonthly\":0,\"validUntil\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.license.plan").value("pro"))
                .andExpect(jsonPath("$.license.validUntil").doesNotExist());

        // La licenza aggiornata vale per l'azienda.
        mvc.perform(get("/api/entitlements").with(TestJwt.user(id, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("pro"));

        mvc.perform(get("/api/platform/tenants/{id}/audit", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("license.update"))
                .andExpect(jsonPath("$[0].actorName").value("Ada Admin"))
                .andExpect(jsonPath("$[1].action").value("tenant.create"));
    }

    @Test
    void duplicateSlugAndUnknownFeaturesAreRejected() throws Exception {
        UUID id = UUID.randomUUID();
        String slug = "dup-" + id.toString().substring(0, 8);
        mvc.perform(post("/api/platform/tenants").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(newTenantJson(id, slug, 5))).andExpect(status().isCreated());
        mvc.perform(post("/api/platform/tenants").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(newTenantJson(UUID.randomUUID(), slug, 5))).andExpect(status().isConflict());
        mvc.perform(put("/api/platform/tenants/{id}/license", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"pro\",\"maxUsers\":5,\"features\":[\"inventato\"],\"aiQuotaMonthly\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/platform/tenants").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(newTenantJson(UUID.randomUUID(), "Sigla Con Spazi", 5))).andExpect(status().isBadRequest());
    }

    @Test
    void suspendedCompanyIsBlockedUntilReactivated() throws Exception {
        mvc.perform(get("/api/me").with(TestJwt.user(existingTenant, "viewer"))).andExpect(status().isOk());

        mvc.perform(put("/api/platform/tenants/{id}", existingTenant).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tenant base\",\"status\":\"suspended\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("suspended"));
        mvc.perform(get("/api/me").with(TestJwt.user(existingTenant, "viewer"))).andExpect(status().isForbidden());

        mvc.perform(put("/api/platform/tenants/{id}", existingTenant).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tenant base\",\"status\":\"active\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(TestJwt.user(existingTenant, "viewer"))).andExpect(status().isOk());
    }

    @Test
    void usersAreCreatedInTheCompanyWithinTheLicenseLimit() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/platform/tenants").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(newTenantJson(id, "lim-" + id.toString().substring(0, 8), 1))).andExpect(status().isCreated());

        String user = "{\"username\":\"mario.rossi\",\"email\":\"mario@rossi.example\",\"firstName\":\"Mario\",\"lastName\":\"Rossi\",\"role\":\"admin\"}";
        mvc.perform(post("/api/platform/tenants/{id}/users", id).with(admin()).contentType(MediaType.APPLICATION_JSON).content(user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("admin"))
                .andExpect(jsonPath("$.temporaryPassword").isString());
        assertThat(identity.users.get(id)).singleElement().satisfies(u -> assertThat(u.password()).hasSize(16));

        // Licenza da 1 utente: il secondo viene rifiutato.
        mvc.perform(post("/api/platform/tenants/{id}/users", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(user.replace("mario", "anna")))
                .andExpect(status().isConflict());

        // Ruolo non valido e utente di un'altra azienda.
        mvc.perform(post("/api/platform/tenants/{id}/users", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(user.replace("\"admin\"", "\"platform-admin\""))).andExpect(status().isBadRequest());
        String otherUser = identity.users.get(id).get(0).user().id();
        mvc.perform(put("/api/platform/tenants/{id}/users/{u}", existingTenant, otherUser).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());

        // Nel registro niente password.
        String details = TenantContext.callAs(id, () -> jdbc.queryForObject(
                "select string_agg(details::text, ' ') from platform_audit where tenant_id = ?", String.class, id));
        assertThat(details).contains("mario.rossi").doesNotContainIgnoringCase("password\":");
    }

    @Test
    void companyEmployeesCannotReadThePlatformAuditOrOtherCompanies() {
        UUID other = TestData.createTenant(tx, tenants, licenses, "pro", Set.of(), 0);
        // Con il tenant nel contesto (sessione di un dipendente) si vede solo la propria azienda.
        List<UUID> visible = TenantContext.callAs(existingTenant,
                () -> tenants.findAll().stream().map(t -> t.getId()).toList());
        assertThat(visible).containsExactly(existingTenant).doesNotContain(other);
    }

    // --- Identità finta, in memoria ---------------------------------------------------------------

    record Stored(TenantUser user, String password) {
    }

    static class FakeIdentity implements IdentityAdmin {
        final Map<UUID, List<Stored>> users = new ConcurrentHashMap<>();

        @Override
        public List<TenantUser> listUsers(UUID tenantId) {
            return users.getOrDefault(tenantId, List.of()).stream().map(Stored::user).toList();
        }

        @Override
        public int countUsers(UUID tenantId) {
            return users.getOrDefault(tenantId, List.of()).size();
        }

        @Override
        public TenantUser createUser(UUID tenantId, NewTenantUser u, String temporaryPassword) {
            TenantUser user = new TenantUser(UUID.randomUUID().toString(), u.username(), u.email(), u.firstName(),
                    u.lastName(), true, u.role(), Instant.now());
            users.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(new Stored(user, temporaryPassword));
            return user;
        }

        @Override
        public TenantUser updateUser(UUID tenantId, String userId, Boolean enabled, String role) {
            return users.getOrDefault(tenantId, List.of()).stream().map(Stored::user)
                    .filter(u -> u.id().equals(userId)).findFirst()
                    .orElseThrow(() -> new IdentityAdminException(IdentityAdminException.Kind.NOT_FOUND, "Utente non trovato"));
        }

        @Override
        public void resetPassword(UUID tenantId, String userId, String temporaryPassword) {
            updateUser(tenantId, userId, null, null);
        }
    }

    @TestConfiguration
    static class FakeIdentityConfig {
        @Bean
        @Primary
        FakeIdentity fakeIdentity() {
            return new FakeIdentity();
        }
    }
}
