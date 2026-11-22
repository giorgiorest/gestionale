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
import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.licensing.LicenseRepository;
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
        TenantContext.runAs(tenantA, () -> customers.save(new Customer(customerA, tenantA, "Cliente A", null)));
        TenantContext.runAs(tenantB, () -> customers.save(new Customer(customerB, tenantB, "Cliente B", null)));
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
                () -> customers.save(new Customer(UUID.randomUUID(), tenantB, "Intruso", null))))
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
