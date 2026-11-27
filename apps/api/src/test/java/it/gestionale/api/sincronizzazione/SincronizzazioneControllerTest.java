package it.gestionale.api.sincronizzazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.TenantRepository;
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
class SincronizzazioneControllerTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions putCustomer(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/customers/" + id).with(JwtDiTest.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteCustomer(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/customers/" + id).with(JwtDiTest.user(tenant, role)));
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

        ResultActions result = sync(JwtDiTest.user(tenantA, "admin"), null, null).andExpect(status().isOk());
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
        ResultActions first = sync(JwtDiTest.user(tenantA, "admin"), null, null).andExpect(status().isOk());
        String cursor = read(first, "$.cursor");

        UUID second = UUID.randomUUID();
        putCustomer(tenantA, "admin", second, "{\"name\":\"Seconda\"}").andExpect(status().isOk());

        sync(JwtDiTest.user(tenantA, "admin"), cursor, null)
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

        ResultActions firstPage = sync(JwtDiTest.user(tenantA, "admin"), null, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.entities.customers.length()").value(2));
        String cursor = read(firstPage, "$.cursor");

        sync(JwtDiTest.user(tenantA, "admin"), cursor, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.entities.customers.length()").value(1));
    }

    @Test
    void tenantsDoNotSeeEachOthersChanges() throws Exception {
        putCustomer(tenantA, "admin", UUID.randomUUID(), "{\"name\":\"Cliente di A\"}").andExpect(status().isOk());

        sync(JwtDiTest.user(tenantB, "admin"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.customers.length()").value(0));
    }

    @Test
    void entityIsOmittedWithoutTheReadPermission() throws Exception {
        sync(JwtDiTest.user(tenantA), null, null)
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
        mvc.perform(put("/api/commesse/" + commessaId).with(JwtDiTest.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customerId + "\",\"nome\":\"Cantiere\",\"stato\":\"pianificata\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/resources/" + resourceId).with(JwtDiTest.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Mario\",\"lastName\":\"Verdi\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/resource-assignments/" + assignmentId).with(JwtDiTest.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceId\":\"" + resourceId + "\",\"commessaId\":\"" + commessaId + "\","
                        + "\"validFrom\":\"2026-01-01\",\"role\":\"operaio\",\"hourlyCostOverride\":20.5,"
                        + "\"active\":true}"))
                .andExpect(status().isOk());

        ResultActions withLaborCost = sync(JwtDiTest.user(tenantA, "admin"), null, null).andExpect(status().isOk());
        Map<String, Object> dataWithCost = read(withLaborCost, "$.entities.resource_assignments[0].data");
        assertThat(((Number) dataWithCost.get("hourlyCostOverride")).doubleValue()).isEqualTo(20.5);

        ResultActions withoutLaborCost = sync(JwtDiTest.user(tenantA, "caposquadra"), null, null)
                .andExpect(status().isOk());
        Map<String, Object> dataWithoutCost = read(withoutLaborCost, "$.entities.resource_assignments[0].data");
        assertThat(dataWithoutCost.get("hourlyCostOverride")).isNull();
    }

    /**
     * {@code attachments:read} da solo non basta: una riga si vede solo se si ha anche il
     * permesso di lettura della sua {@code entityType} (qui {@code daily_reports}) — altrimenti
     * {@code amministrativo} (ha {@code attachments:read} ma non {@code daily-reports:read})
     * vedrebbe da qui i metadati di un rapportino che non può leggere direttamente.
     */
    @Test
    void attachmentRowIsFilteredByItsLinkedEntityPermission() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        mvc.perform(post("/api/attachments/upload-url").with(JwtDiTest.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + attachmentId + "\",\"entityType\":\"daily_reports\",\"entityId\":\""
                        + entityId + "\",\"filename\":\"cantiere.jpg\",\"mimeType\":\"image/jpeg\","
                        + "\"sizeBytes\":1024}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/attachments/" + attachmentId).with(JwtDiTest.user(tenantA, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sha256\":\"" + "a".repeat(64) + "\"}"))
                .andExpect(status().isOk());

        // operator ha sia attachments:read sia daily-reports:read: la vede.
        sync(JwtDiTest.user(tenantA, "operator"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.attachments.length()").value(1));

        // amministrativo ha attachments:read ma non daily-reports:read: non deve vederla.
        sync(JwtDiTest.user(tenantA, "amministrativo"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.attachments.length()").value(0));
    }

    @Test
    void malformedCursorIsRejected() throws Exception {
        sync(JwtDiTest.user(tenantA, "admin"), "non-un-cursore-valido!!", null).andExpect(status().isBadRequest());
    }
}
