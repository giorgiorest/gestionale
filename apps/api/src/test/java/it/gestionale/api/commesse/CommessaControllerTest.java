package it.gestionale.api.commesse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantContext;
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
class CommessaControllerTest extends AbstractIntegrationTest {

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

    UUID tenantA;
    UUID tenantB;
    UUID customerA;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        customerA = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(
                s -> customers.save(new Customer(customerA, tenantA, "Cliente A", null, null))));
    }

    private ResultActions putCommessa(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/commesse/" + id).with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteCommessa(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/commesse/" + id).with(TestJwt.user(tenant, role)));
    }

    private Commessa row(UUID tenant, UUID id) {
        return TenantContext.callAs(tenant, () -> commesse.findByIdAndTenantId(id, tenant)).orElseThrow();
    }

    @Test
    void amministrativoCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"stato\":\"pianificata\"}";

        putCommessa(tenantA, "amministrativo", id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.stato").value("pianificata"))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putCommessa(tenantA, "amministrativo", id, body).andExpect(status().isOk());

        String updated = "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"indirizzo\":\"Via Roma 1\",\"stato\":\"in_corso\"}";
        putCommessa(tenantA, "amministrativo", id, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indirizzo").value("Via Roma 1"))
                .andExpect(jsonPath("$.stato").value("in_corso"))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/commesse").with(TestJwt.user(tenantA, "operaio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stato").value("in_corso"));
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Da cancellare\",\"stato\":\"pianificata\"}")
                .andExpect(status().isOk());

        deleteCommessa(tenantA, "amministrativo", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/commesse").with(TestJwt.user(tenantA, "capocantiere")))
                .andExpect(jsonPath("$.length()").value(0));
        Commessa deleted = row(tenantA, id);
        assertThat(deleted.isDeleted()).isTrue();

        deleteCommessa(tenantA, "amministrativo", id).andExpect(status().isNoContent());
        assertThat(row(tenantA, id).getDeletedAt()).isEqualTo(deleted.getDeletedAt());

        deleteCommessa(tenantA, "amministrativo", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void capocantiereCannotWrite() throws Exception {
        putCommessa(tenantA, "capocantiere", UUID.randomUUID(),
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"X\",\"stato\":\"pianificata\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void commesseOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Di A\",\"stato\":\"pianificata\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse").with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        putCommessa(tenantB, "admin", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Furto\",\"stato\":\"pianificata\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void customerMustBeAnActiveCustomerOfTheSameTenant() throws Exception {
        UUID unknown = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + unknown + "\",\"nome\":\"X\",\"stato\":\"pianificata\"}")
                .andExpect(status().isBadRequest());

        UUID customerB = UUID.randomUUID();
        TenantContext.runAs(tenantB, () -> tx.executeWithoutResult(
                s -> customers.save(new Customer(customerB, tenantB, "Cliente B", null, null))));
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + customerB + "\",\"nome\":\"X\",\"stato\":\"pianificata\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + customerA + "\",\"nome\":\" \",\"stato\":\"pianificata\"}")
                .andExpect(status().isBadRequest());
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"X\",\"stato\":\"non_esiste\"}")
                .andExpect(status().isBadRequest());
    }
}
