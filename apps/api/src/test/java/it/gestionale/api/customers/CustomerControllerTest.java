package it.gestionale.api.customers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class CustomerControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions putCustomer(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/customers/" + id)
                .with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void operatorCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();

        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi S.r.l.\",\"email\":\"info@rossi.example\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi S.r.l.\",\"email\":\"info@rossi.example\"}")
                .andExpect(status().isOk());
        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi S.p.A.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rossi S.p.A."));

        mvc.perform(get("/api/customers").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Rossi S.p.A."))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void viewerCannotWrite() throws Exception {
        putCustomer(tenantA, "viewer", UUID.randomUUID(), "{\"name\":\"Bianchi\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithoutRolesCannotRead() throws Exception {
        mvc.perform(get("/api/customers").with(TestJwt.user(tenantA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customersOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putCustomer(tenantA, "operator", id, "{\"name\":\"Cliente di A\"}").andExpect(status().isOk());

        mvc.perform(get("/api/customers").with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        // Stesso id da un altro tenant: la riga non è visibile e l'inserimento va in conflitto.
        putCustomer(tenantB, "admin", id, "{\"name\":\"Furto\"}").andExpect(status().isConflict());

        mvc.perform(get("/api/customers").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(jsonPath("$[0].name").value("Cliente di A"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\" \"}").andExpect(status().isBadRequest());
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"X\",\"email\":\"non-una-email\"}")
                .andExpect(status().isBadRequest());
    }
}
