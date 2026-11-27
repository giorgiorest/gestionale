package it.gestionale.api.assegnazioni;

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
import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.StatoCommessa;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.risorse.Risorsa;
import it.gestionale.api.risorse.RisorsaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class AssegnazioneRisorsaControllerTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;
    @Autowired
    ClienteRepository customers;
    @Autowired
    CommessaRepository commesse;
    @Autowired
    RisorsaRepository resources;
    @Autowired
    AssegnazioneRisorsaRepository assignments;

    UUID tenantA;
    UUID tenantB;
    UUID resourceA;
    UUID commessaA1;
    UUID commessaA2;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        resourceA = UUID.randomUUID();
        commessaA1 = UUID.randomUUID();
        commessaA2 = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerA, tenantA, "Cliente A", null, null));
            resources.save(new Risorsa(resourceA, tenantA, "Mario", "Rossi", null, null, "u-a"));
            commesse.save(new Commessa(commessaA1, tenantA, customerA, "Tetto", null, StatoCommessa.PIANIFICATA,
                    "u-a"));
            commesse.save(new Commessa(commessaA2, tenantA, customerA, "Muro", null, StatoCommessa.PIANIFICATA,
                    "u-a"));
        }));
    }

    private ResultActions putAssignment(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/resource-assignments/" + id).with(JwtDiTest.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteAssignment(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/resource-assignments/" + id).with(JwtDiTest.user(tenant, role)));
    }

    private String body(UUID resourceId, UUID commessaId, String role, String validFrom, String validTo) {
        return "{\"resourceId\":\"" + resourceId + "\",\"commessaId\":\"" + commessaId + "\",\"validFrom\":\""
                + validFrom + "\",\"validTo\":" + (validTo == null ? "null" : "\"" + validTo + "\"") + ",\"role\":\""
                + role + "\",\"active\":true}";
    }

    private String bodyWithCost(UUID resourceId, UUID commessaId, String role, String validFrom,
                                String hourlyCostOverride) {
        return "{\"resourceId\":\"" + resourceId + "\",\"commessaId\":\"" + commessaId + "\",\"validFrom\":\""
                + validFrom + "\",\"validTo\":null,\"role\":\"" + role + "\",\"hourlyCostOverride\":"
                + hourlyCostOverride + ",\"active\":true}";
    }

    @Test
    void capocantiereCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        String json = body(resourceA, commessaA1, "capocantiere", "2026-01-01", null);

        putAssignment(tenantA, "capocantiere", id, json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.role").value("capocantiere"))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putAssignment(tenantA, "capocantiere", id, json).andExpect(status().isOk());

        String updated = body(resourceA, commessaA1, "operaio", "2026-01-01", "2026-06-30");
        putAssignment(tenantA, "capocantiere", id, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("operaio"))
                .andExpect(jsonPath("$.validTo").value("2026-06-30"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void sameResourceCanBeAssignedToDifferentCommesseWithDifferentPeriodsAndRoles() throws Exception {
        putAssignment(tenantA, "capocantiere", UUID.randomUUID(),
                body(resourceA, commessaA1, "capocantiere", "2026-01-01", "2026-03-31"))
                .andExpect(status().isOk());
        putAssignment(tenantA, "capocantiere", UUID.randomUUID(),
                body(resourceA, commessaA2, "operaio", "2026-04-01", null))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA1 + "/resource-assignments").with(JwtDiTest.user(tenantA, "operaio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("capocantiere"));
        mvc.perform(get("/api/commesse/" + commessaA2 + "/resource-assignments").with(JwtDiTest.user(tenantA, "operaio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("operaio"));
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putAssignment(tenantA, "capocantiere", id, body(resourceA, commessaA1, "operaio", "2026-01-01", null))
                .andExpect(status().isOk());

        deleteAssignment(tenantA, "capocantiere", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/commesse/" + commessaA1 + "/resource-assignments").with(JwtDiTest.user(tenantA, "operaio")))
                .andExpect(jsonPath("$.length()").value(0));
        AssegnazioneRisorsa deleted = ContestoTenant.callAs(tenantA, () -> assignments.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        deleteAssignment(tenantA, "capocantiere", id).andExpect(status().isNoContent());
        deleteAssignment(tenantA, "capocantiere", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void caposquadraCannotWrite() throws Exception {
        putAssignment(tenantA, "caposquadra", UUID.randomUUID(),
                body(resourceA, commessaA1, "operaio", "2026-01-01", null))
                .andExpect(status().isForbidden());
    }

    /**
     * {@code hourlyCostOverride} è un costo (retribuzione implicita contestuale alla commessa)
     * tanto quanto la tariffa base di {@code Risorsa} (#53, ADR 0021): va nascosto a chi ha
     * {@code resource-assignments:read} ma non {@code labor-cost:read}.
     */
    @Test
    void hourlyCostOverrideIsHiddenFromRolesWithoutLaborCostRead() throws Exception {
        UUID id = UUID.randomUUID();
        putAssignment(tenantA, "capocantiere", id, bodyWithCost(resourceA, commessaA1, "operaio", "2026-01-01", "15.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hourlyCostOverride").value(15.00));

        // caposquadra e operaio: resource-assignments:read senza labor-cost:read.
        for (String role : new String[] { "caposquadra", "operaio" }) {
            mvc.perform(get("/api/commesse/" + commessaA1 + "/resource-assignments").with(JwtDiTest.user(tenantA, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].hourlyCostOverride").doesNotExist());
        }

        // capocantiere, operator, viewer, admin: hanno anche labor-cost:read.
        for (String role : new String[] { "capocantiere", "operator", "viewer", "admin" }) {
            mvc.perform(get("/api/commesse/" + commessaA1 + "/resource-assignments").with(JwtDiTest.user(tenantA, role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].hourlyCostOverride").value(15.00));
        }
    }

    @Test
    void assignmentsOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putAssignment(tenantA, "capocantiere", id, body(resourceA, commessaA1, "operaio", "2026-01-01", null))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA1 + "/resource-assignments").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID resourceB = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID commessaB = UUID.randomUUID();
        ContestoTenant.runAs(tenantB, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerB, tenantB, "Cliente B", null, null));
            resources.save(new Risorsa(resourceB, tenantB, "Luigi", "Bianchi", null, null, "u-b"));
            commesse.save(new Commessa(commessaB, tenantB, customerB, "Furto", null, StatoCommessa.PIANIFICATA,
                    "u-b"));
        }));
        putAssignment(tenantB, "admin", id, body(resourceB, commessaB, "operaio", "2026-01-01", null))
                .andExpect(status().isConflict());
    }

    @Test
    void resourceAndCommessaMustBeActiveAndOfTheSameTenant() throws Exception {
        UUID unknownResource = UUID.randomUUID();
        putAssignment(tenantA, "capocantiere", UUID.randomUUID(),
                body(unknownResource, commessaA1, "operaio", "2026-01-01", null))
                .andExpect(status().isBadRequest());

        UUID unknownCommessa = UUID.randomUUID();
        putAssignment(tenantA, "capocantiere", UUID.randomUUID(),
                body(resourceA, unknownCommessa, "operaio", "2026-01-01", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        // validTo precedente a validFrom.
        putAssignment(tenantA, "capocantiere", UUID.randomUUID(),
                body(resourceA, commessaA1, "operaio", "2026-06-01", "2026-01-01"))
                .andExpect(status().isBadRequest());
        // Ruolo vuoto.
        putAssignment(tenantA, "capocantiere", UUID.randomUUID(),
                body(resourceA, commessaA1, " ", "2026-01-01", null))
                .andExpect(status().isBadRequest());
    }
}
