package it.gestionale.api.fornitori;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.ContestoTenant;
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

/** Stesso contratto di {@code ClienteControllerTest}, tabella ed endpoint propri (roadmap 2.2). */
@AutoConfigureMockMvc
class FornitoreControllerTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;
    @Autowired
    FornitoreRepository suppliers;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions putSupplier(JwtRequestPostProcessor jwt, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/suppliers/" + id)
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions putSupplier(UUID tenant, String role, UUID id, String json) throws Exception {
        return putSupplier(JwtDiTest.user(tenant, role), id, json);
    }

    private ResultActions deleteSupplier(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/suppliers/" + id).with(JwtDiTest.user(tenant, role)));
    }

    private Fornitore row(UUID tenant, UUID id) {
        return ContestoTenant.callAs(tenant, () -> suppliers.findByIdAndTenantId(id, tenant)).orElseThrow();
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

        mvc.perform(get("/api/suppliers").with(JwtDiTest.user(tenantA, "viewer")))
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

        mvc.perform(get("/api/suppliers").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(0));
        Fornitore deleted = row(tenantA, id);
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
        mvc.perform(get("/api/suppliers").with(JwtDiTest.user(tenantA))).andExpect(status().isForbidden());
    }

    /** Isolamento per tenant: stesso controllo di {@code IsolamentoTenantTest}, qui sull'endpoint. */
    @Test
    void suppliersOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putSupplier(tenantA, "operator", id, "{\"name\":\"Fornitore di A\"}").andExpect(status().isOk());

        mvc.perform(get("/api/suppliers").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        putSupplier(tenantB, "admin", id, "{\"name\":\"Furto\"}").andExpect(status().isConflict());
    }
}
