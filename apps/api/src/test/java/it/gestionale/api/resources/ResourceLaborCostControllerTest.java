package it.gestionale.api.resources;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Tariffa oraria base della risorsa (#53): permesso dedicato {@code labor-cost:read/write},
 * diverso da {@code resources:read/write} (vedi ADR 0021) — questi test coprono esattamente la
 * distribuzione dei ruoli decisa lì, non quella di {@code ResourceControllerTest}.
 */
@AutoConfigureMockMvc
class ResourceLaborCostControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;
    @Autowired
    ResourceRepository resources;

    UUID tenantA;
    UUID resourceA;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        resourceA = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(
                s -> resources.save(new Resource(resourceA, tenantA, "Mario", "Rossi", null, null, "u-a"))));
    }

    private ResultActions getLaborCost(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(get("/api/resources/" + id + "/labor-cost").with(TestJwt.user(tenant, role)));
    }

    private ResultActions putLaborCost(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/resources/" + id + "/labor-cost").with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void capocantiereSetsAndReadsTheStandardHourlyCost() throws Exception {
        putLaborCost(tenantA, "capocantiere", resourceA, "{\"standardHourlyCost\":18.50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(resourceA.toString()))
                .andExpect(jsonPath("$.standardHourlyCost").value(18.50));

        getLaborCost(tenantA, "capocantiere", resourceA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standardHourlyCost").value(18.50));
    }

    @Test
    void operatorAndViewerCanReadButOnlyOperatorCanWrite() throws Exception {
        putLaborCost(tenantA, "operator", resourceA, "{\"standardHourlyCost\":20}").andExpect(status().isOk());
        getLaborCost(tenantA, "operator", resourceA).andExpect(status().isOk());
        getLaborCost(tenantA, "viewer", resourceA).andExpect(status().isOk());
        putLaborCost(tenantA, "viewer", resourceA, "{\"standardHourlyCost\":21}").andExpect(status().isForbidden());
    }

    @Test
    void caposquadraOperaioAndAmministrativoHaveNoPermissionAtAll() throws Exception {
        for (String role : new String[] { "caposquadra", "operaio", "amministrativo" }) {
            getLaborCost(tenantA, role, resourceA).andExpect(status().isForbidden());
            putLaborCost(tenantA, role, resourceA, "{\"standardHourlyCost\":15}").andExpect(status().isForbidden());
        }
    }

    @Test
    void anUnknownIdIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        getLaborCost(tenantA, "admin", unknown).andExpect(status().isNotFound());
        putLaborCost(tenantA, "admin", unknown, "{\"standardHourlyCost\":15}").andExpect(status().isNotFound());
    }

    @Test
    void aDeletedResourceIsNotFound() throws Exception {
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            Resource resource = resources.findByIdAndTenantId(resourceA, tenantA).orElseThrow();
            resource.markDeleted("u-a", java.time.Instant.now());
        }));
        getLaborCost(tenantA, "admin", resourceA).andExpect(status().isNotFound());
    }

    @Test
    void rejectsANegativeCost() throws Exception {
        putLaborCost(tenantA, "admin", resourceA, "{\"standardHourlyCost\":-1}").andExpect(status().isBadRequest());
    }

    @Test
    void aResourceOfAnotherTenantIsNotVisible() throws Exception {
        UUID tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        getLaborCost(tenantB, "admin", resourceA).andExpect(status().isNotFound());
    }

    @Test
    void settingTheCostDoesNotTouchOtherFields() throws Exception {
        putLaborCost(tenantA, "admin", resourceA, "{\"standardHourlyCost\":12}").andExpect(status().isOk());
        Resource reloaded = TenantContext.callAs(tenantA, () -> resources.findByIdAndTenantId(resourceA, tenantA))
                .orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Mario");
        assertThat(reloaded.getLastName()).isEqualTo("Rossi");
    }
}
