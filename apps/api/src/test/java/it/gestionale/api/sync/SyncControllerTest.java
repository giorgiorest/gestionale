package it.gestionale.api.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class SyncControllerTest extends AbstractIntegrationTest {

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
        return mvc.perform(put("/api/customers/" + id).with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteCustomer(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/customers/" + id).with(TestJwt.user(tenant, role)));
    }

    private ResultActions sync(JwtRequestPostProcessor jwt, String cursor, Integer limit) throws Exception {
        var request = get("/api/sync/changes").with(jwt);
        if (cursor != null) {
            request = request.param("cursor", cursor);
        }
        if (limit != null) {
            request = request.param("limit", String.valueOf(limit));
        }
        return mvc.perform(request);
    }

    private String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    @Test
    void changesIncludeCreatedRecordsAndTombstones() throws Exception {
        UUID active = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        putCustomer(tenantA, "admin", active, "{\"name\":\"Rossi\"}").andExpect(status().isOk());
        putCustomer(tenantA, "admin", deleted, "{\"name\":\"Da cancellare\"}").andExpect(status().isOk());
        deleteCustomer(tenantA, "admin", deleted).andExpect(status().isNoContent());

        ResultActions result = sync(TestJwt.user(tenantA, "admin"), null, null).andExpect(status().isOk());
        List<Map<String, Object>> changes = read(result, "$.entities.customers");
        assertThat(changes).hasSize(2);

        Map<String, Object> activeChange = changes.stream().filter(c -> c.get("id").equals(active.toString()))
                .findFirst().orElseThrow();
        assertThat(activeChange.get("deleted")).isEqualTo(false);
        assertThat(((Map<?, ?>) activeChange.get("data")).get("name")).isEqualTo("Rossi");

        Map<String, Object> deletedChange = changes.stream().filter(c -> c.get("id").equals(deleted.toString()))
                .findFirst().orElseThrow();
        assertThat(deletedChange.get("deleted")).isEqualTo(true);
        assertThat(deletedChange.get("data")).isNull();
    }

    @Test
    void cursorSkipsChangesAlreadySeen() throws Exception {
        putCustomer(tenantA, "admin", UUID.randomUUID(), "{\"name\":\"Prima\"}").andExpect(status().isOk());
        ResultActions first = sync(TestJwt.user(tenantA, "admin"), null, null).andExpect(status().isOk());
        String cursor = read(first, "$.cursor");

        UUID second = UUID.randomUUID();
        putCustomer(tenantA, "admin", second, "{\"name\":\"Seconda\"}").andExpect(status().isOk());

        sync(TestJwt.user(tenantA, "admin"), cursor, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.customers.length()").value(1))
                .andExpect(jsonPath("$.entities.customers[0].id").value(second.toString()));
    }

    @Test
    void pagesWhenMoreChangesThanTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            putCustomer(tenantA, "admin", UUID.randomUUID(), "{\"name\":\"Cliente " + i + "\"}")
                    .andExpect(status().isOk());
        }

        ResultActions firstPage = sync(TestJwt.user(tenantA, "admin"), null, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.entities.customers.length()").value(2));
        String cursor = read(firstPage, "$.cursor");

        sync(TestJwt.user(tenantA, "admin"), cursor, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.entities.customers.length()").value(1));
    }

    @Test
    void tenantsDoNotSeeEachOthersChanges() throws Exception {
        putCustomer(tenantA, "admin", UUID.randomUUID(), "{\"name\":\"Cliente di A\"}").andExpect(status().isOk());

        sync(TestJwt.user(tenantB, "admin"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.customers.length()").value(0));
    }

    @Test
    void entityIsOmittedWithoutTheReadPermission() throws Exception {
        sync(TestJwt.user(tenantA), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.customers").doesNotExist());
    }

    @Test
    void hourlyCostOverrideIsHiddenWithoutLaborCostRead() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID commessaId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        putCustomer(tenantA, "admin", customerId, "{\"name\":\"Cliente\"}").andExpect(status().isOk());
        mvc.perform(put("/api/commesse/" + commessaId).with(TestJwt.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customerId + "\",\"nome\":\"Cantiere\",\"stato\":\"pianificata\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/resources/" + resourceId).with(TestJwt.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Mario\",\"lastName\":\"Verdi\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/resource-assignments/" + assignmentId).with(TestJwt.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceId\":\"" + resourceId + "\",\"commessaId\":\"" + commessaId + "\","
                        + "\"validFrom\":\"2026-01-01\",\"role\":\"operaio\",\"hourlyCostOverride\":20.5,"
                        + "\"active\":true}"))
                .andExpect(status().isOk());

        ResultActions withLaborCost = sync(TestJwt.user(tenantA, "admin"), null, null).andExpect(status().isOk());
        Map<String, Object> dataWithCost = read(withLaborCost, "$.entities.resource_assignments[0].data");
        assertThat(((Number) dataWithCost.get("hourlyCostOverride")).doubleValue()).isEqualTo(20.5);

        ResultActions withoutLaborCost = sync(TestJwt.user(tenantA, "caposquadra"), null, null)
                .andExpect(status().isOk());
        Map<String, Object> dataWithoutCost = read(withoutLaborCost, "$.entities.resource_assignments[0].data");
        assertThat(dataWithoutCost.get("hourlyCostOverride")).isNull();
    }

    @Test
    void malformedCursorIsRejected() throws Exception {
        sync(TestJwt.user(tenantA, "admin"), "non-un-cursore-valido!!", null).andExpect(status().isBadRequest());
    }
}
