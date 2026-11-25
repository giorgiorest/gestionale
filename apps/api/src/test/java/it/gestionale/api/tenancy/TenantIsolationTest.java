package it.gestionale.api.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.CommessaStato;
import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.resources.Resource;
import it.gestionale.api.resources.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifica che la Row Level Security isoli i dati di due tenant. */
@AutoConfigureMockMvc
class TenantIsolationTest extends AbstractIntegrationTest {

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
    ResourceRepository resources;
    @Autowired
    CommessaRepository commesse;
    @Autowired
    JdbcTemplate jdbc;

    UUID tenantA;
    UUID tenantB;
    UUID customerA;
    UUID customerB;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = TestData.createTenant(tx, tenants, licenses, "pro", Set.of("aiAssistant"), 100);
        customerA = UUID.randomUUID();
        customerB = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> customers.save(new Customer(customerA, tenantA, "Cliente A", null, "u-a")));
        TenantContext.runAs(tenantB, () -> customers.save(new Customer(customerB, tenantB, "Cliente B", null, "u-b")));
    }

    @Test
    void applicationRoleIsNotSuperuserAndCannotBypassRls() {
        Boolean bypass = jdbc.queryForObject(
                "select rolsuper or rolbypassrls from pg_roles where rolname = current_user", Boolean.class);
        assertThat(bypass).isFalse();
    }

    @Test
    void eachTenantSeesOnlyItsOwnRows() {
        List<Customer> seenByA = TenantContext.callAs(tenantA, customers::findAll);
        List<Customer> seenByB = TenantContext.callAs(tenantB, customers::findAll);

        assertThat(seenByA).extracting(Customer::getTenantId).containsOnly(tenantA);
        assertThat(seenByA).extracting(Customer::getId).contains(customerA).doesNotContain(customerB);
        assertThat(seenByB).extracting(Customer::getId).contains(customerB).doesNotContain(customerA);
        assertThat(TenantContext.callAs(tenantA, () -> customers.findById(customerB))).isEmpty();
        assertThat(TenantContext.callAs(tenantA, () -> tenants.findAll()))
                .extracting(Tenant::getId).containsExactly(tenantA);
    }

    @Test
    void withoutTenantNothingIsVisible() {
        assertThat(TenantContext.current()).isEmpty();
        assertThat(customers.findAll()).isEmpty();
        assertThat(tenants.findAll()).isEmpty();
    }

    @Test
    void cannotWriteRowsOfAnotherTenant() {
        assertThatThrownBy(() -> TenantContext.runAs(tenantA,
                () -> customers.save(new Customer(UUID.randomUUID(), tenantB, "Intruso", null, "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    /**
     * Stesso controllo di {@link #eachTenantSeesOnlyItsOwnRows} e {@link #cannotWriteRowsOfAnotherTenant},
     * ma su {@code resources}: non basta che il meccanismo RLS funzioni (lo prova già Customer),
     * serve verificare che sia stato davvero attivato su questa tabella (una nuova entità che
     * dimentica {@code enable_tenant_rls} in una migrazione non darebbe altrimenti nessun errore).
     */
    @Test
    void resourcesAreIsolatedByTenant() {
        UUID resourceA = UUID.randomUUID();
        UUID resourceB = UUID.randomUUID();
        TenantContext.runAs(tenantA,
                () -> resources.save(new Resource(resourceA, tenantA, "Mario", "Rossi", null, null, "u-a")));
        TenantContext.runAs(tenantB,
                () -> resources.save(new Resource(resourceB, tenantB, "Luigi", "Bianchi", null, null, "u-b")));

        List<Resource> seenByA = TenantContext.callAs(tenantA, resources::findAll);
        assertThat(seenByA).extracting(Resource::getId).containsExactly(resourceA);
        assertThatThrownBy(() -> TenantContext.runAs(tenantA,
                () -> resources.save(new Resource(UUID.randomUUID(), tenantB, "Intruso", "Intruso", null, null, "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    /** Come {@link #resourcesAreIsolatedByTenant}, ma su {@code commesse}. */
    @Test
    void commesseAreIsolatedByTenant() {
        UUID commessaA = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> commesse.save(
                new Commessa(commessaA, tenantA, customerA, "Tetto", null, CommessaStato.PIANIFICATA, "u-a")));

        List<Commessa> seenByA = TenantContext.callAs(tenantA, commesse::findAll);
        assertThat(seenByA).extracting(Commessa::getId).containsExactly(commessaA);
        assertThatThrownBy(() -> TenantContext.runAs(tenantA, () -> commesse.save(new Commessa(UUID.randomUUID(),
                tenantB, customerB, "Furto", null, CommessaStato.PIANIFICATA, "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void entitlementsEndpointReturnsDataOfTheTokenTenant() throws Exception {
        mvc.perform(get("/api/entitlements").with(jwt().jwt(j -> j.claim("tenant_id", tenantA.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("base"));
        mvc.perform(get("/api/entitlements").with(jwt().jwt(j -> j.claim("tenant_id", tenantB.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("pro"))
                .andExpect(jsonPath("$.features[0]").value("aiAssistant"));
    }

    @Test
    void requestsWithoutValidTokenOrTenantAreRejected() throws Exception {
        mvc.perform(get("/api/entitlements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/entitlements").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/api/entitlements").with(jwt().jwt(j -> j.claim("tenant_id", "non-un-uuid"))))
                .andExpect(status().isForbidden());
    }
}
