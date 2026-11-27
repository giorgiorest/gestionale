package it.gestionale.api.accesso;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class ProfiloControllerTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;

    @Test
    void returnsProfileTenantRolesAndPermissions() throws Exception {
        UUID tenant = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);

        mvc.perform(get("/api/me").with(JwtDiTest.user(tenant, "viewer", "offline_access")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mario.rossi"))
                .andExpect(jsonPath("$.name").value("Mario Rossi"))
                .andExpect(jsonPath("$.tenant.id").value(tenant.toString()))
                .andExpect(jsonPath("$.tenant.kind").value("customer"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("viewer"))
                .andExpect(jsonPath("$.permissions[0]").value("customers:read"))
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "customers:read", "suppliers:read", "resources:read", "commesse:read",
                        "resource-assignments:read", "work-activities:read", "daily-reports:read",
                        "work-entries:read", "daily-report-materials:read", "labor-cost:read",
                        "attachments:read")))
                .andExpect(jsonPath("$.permissions.length()").value(11));
    }

    @Test
    void unknownTenantHasNullTenantInfo() throws Exception {
        mvc.perform(get("/api/me").with(JwtDiTest.user(UUID.randomUUID(), "operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant").doesNotExist());
    }
}
