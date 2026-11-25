package it.gestionale.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class MeControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;

    @Test
    void returnsProfileTenantRolesAndPermissions() throws Exception {
        UUID tenant = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);

        mvc.perform(get("/api/me").with(TestJwt.user(tenant, "viewer", "offline_access")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mario.rossi"))
                .andExpect(jsonPath("$.name").value("Mario Rossi"))
                .andExpect(jsonPath("$.tenant.id").value(tenant.toString()))
                .andExpect(jsonPath("$.tenant.kind").value("customer"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("viewer"))
                .andExpect(jsonPath("$.permissions[0]").value("customers:read"))
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "customers:read", "resources:read", "commesse:read", "resource-assignments:read",
                        "work-activities:read", "daily-reports:read")))
                .andExpect(jsonPath("$.permissions.length()").value(6));
    }

    @Test
    void unknownTenantHasNullTenantInfo() throws Exception {
        mvc.perform(get("/api/me").with(TestJwt.user(UUID.randomUUID(), "operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant").doesNotExist());
    }
}
