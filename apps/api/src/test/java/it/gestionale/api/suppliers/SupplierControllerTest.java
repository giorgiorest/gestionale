package it.gestionale.api.suppliers;

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
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantContext;
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

/** Stesso contratto di {@code CustomerControllerTest}, tabella ed endpoint propri (roadmap 2.2). */
@AutoConfigureMockMvc
class SupplierControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;
    @Autowired
    SupplierRepository suppliers;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions putSupplier(JwtRequestPostProcessor jwt, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/suppliers/" + id)
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions putSupplier(UUID tenant, String role, UUID id, String json) throws Exception {
        return putSupplier(TestJwt.user(tenant, role), id, json);
    }

    private ResultActions deleteSupplier(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/suppliers/" + id).with(TestJwt.user(tenant, role)));
    }

    private Supplier row(UUID tenant, UUID id) {
        return TenantContext.callAs(tenant, () -> suppliers.findByIdAndTenantId(id, tenant)).orElseThrow();
    }

    @Test
    void operatorCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();

        putSupplier(tenantA, "operator", id, "{\"name\":\"Edilcantieri S.r.l.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.version").value(0));
        putSupplier(tenantA, "operator", id, "{\"name\":\"Edilcantieri S.r.l.\"}")
                .andExpect(status().isOk());
        putSupplier(tenantA, "operator", id, "{\"name\":\"Edilcantieri S.p.A.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Edilcantieri S.p.A."))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/suppliers").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Edilcantieri S.p.A."));
    }

    /** Stessi campi di customers (dopo la voce 2.1), ma tabella propria. */
    @Test
    void storesFiscalAndCommercialData() throws Exception {
        UUID id = UUID.randomUUID();
        putSupplier(tenantA, "operator", id, """
                {"name":"Forniture Edili S.p.A.","email":"info@forniture.example","partitaIva":"12345678901",
                 "codiceFiscale":"12345678901","indirizzo":"Via Industria 5, Bergamo","pec":"forniture@pec.example",
                 "codiceSdi":"ABC1234","condizioniPagamento":"60 giorni data fattura"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitaIva").value("12345678901"))
                .andExpect(jsonPath("$.pec").value("forniture@pec.example"))
                .andExpect(jsonPath("$.condizioniPagamento").value("60 giorni data fattura"));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putSupplier(tenantA, "operator", UUID.randomUUID(), "{\"name\":\" \"}").andExpect(status().isBadRequest());
        putSupplier(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"X\",\"partitaIva\":\"123\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putSupplier(tenantA, "operator", id, "{\"name\":\"Da cancellare\"}").andExpect(status().isOk());

        deleteSupplier(tenantA, "operator", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/suppliers").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(0));
        Supplier deleted = row(tenantA, id);
        assertThat(deleted.isDeleted()).isTrue();

        // Reinvio dalla outbox: nessun errore, nessun cambiamento.
        deleteSupplier(tenantA, "operator", id).andExpect(status().isNoContent());
        assertThat(row(tenantA, id).getDeletedAt()).isEqualTo(deleted.getDeletedAt());

        deleteSupplier(tenantA, "operator", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void viewerCannotWrite() throws Exception {
        putSupplier(tenantA, "viewer", UUID.randomUUID(), "{\"name\":\"Bianchi\"}")
                .andExpect(status().isForbidden());
        deleteSupplier(tenantA, "viewer", UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    void userWithoutRolesCannotRead() throws Exception {
        mvc.perform(get("/api/suppliers").with(TestJwt.user(tenantA))).andExpect(status().isForbidden());
    }

    /** Isolamento per tenant: stesso controllo di {@code TenantIsolationTest}, qui sull'endpoint. */
    @Test
    void suppliersOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putSupplier(tenantA, "operator", id, "{\"name\":\"Fornitore di A\"}").andExpect(status().isOk());

        mvc.perform(get("/api/suppliers").with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        putSupplier(tenantB, "admin", id, "{\"name\":\"Furto\"}").andExpect(status().isConflict());
    }
}
