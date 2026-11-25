package it.gestionale.api.workactivities;

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
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.CommessaStato;
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
class WorkActivityControllerTest extends AbstractIntegrationTest {

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
    @Autowired
    WorkActivityRepository activities;

    UUID tenantA;
    UUID tenantB;
    UUID commessaA;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        commessaA = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerA, tenantA, "Cliente A", null, null));
            commesse.save(new Commessa(commessaA, tenantA, customerA, "Tetto", null, CommessaStato.PIANIFICATA,
                    "u-a"));
        }));
    }

    private ResultActions putActivity(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/work-activities/" + id).with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteActivity(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/work-activities/" + id).with(TestJwt.user(tenant, role)));
    }

    private String body(UUID commessaId, UUID parentId, String code, String status, int progress) {
        return "{\"commessaId\":\"" + commessaId + "\",\"parentId\":" + (parentId == null ? "null" : "\"" + parentId + "\"")
                + ",\"code\":\"" + code + "\",\"name\":\"Armatura pilastri\",\"status\":\"" + status
                + "\",\"progressPercentage\":" + progress + "}";
    }

    @Test
    void capocantiereCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        String json = body(commessaA, null, "ARM-01", "pianificata", 0);

        putActivity(tenantA, "capocantiere", id, json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("pianificata"))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putActivity(tenantA, "capocantiere", id, json).andExpect(status().isOk());

        String updated = body(commessaA, null, "ARM-01", "in_corso", 40);
        putActivity(tenantA, "capocantiere", id, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_corso"))
                .andExpect(jsonPath("$.progressPercentage").value(40))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void listsActivitiesOfTheCommessaOrderedByCode() throws Exception {
        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, null, "B-01", "pianificata", 0))
                .andExpect(status().isOk());
        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, null, "A-01", "pianificata", 0))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA + "/work-activities").with(TestJwt.user(tenantA, "operaio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("A-01"))
                .andExpect(jsonPath("$[1].code").value("B-01"));
    }

    @Test
    void parentMustBeAnActiveActivityOfTheSameCommessa() throws Exception {
        UUID parentId = UUID.randomUUID();
        putActivity(tenantA, "capocantiere", parentId, body(commessaA, null, "WBS-1", "pianificata", 0))
                .andExpect(status().isOk());

        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, parentId, "WBS-1.1", "pianificata", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));

        // Un padre inesistente è rifiutato.
        putActivity(tenantA, "capocantiere", UUID.randomUUID(),
                body(commessaA, UUID.randomUUID(), "WBS-1.2", "pianificata", 0))
                .andExpect(status().isBadRequest());

        // Una lavorazione non può essere padre di se stessa.
        UUID selfId = UUID.randomUUID();
        putActivity(tenantA, "capocantiere", selfId, body(commessaA, selfId, "SELF", "pianificata", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movingAnActivityToAnotherCommessaRevalidatesAnUnchangedParent() throws Exception {
        UUID parentId = UUID.randomUUID();
        putActivity(tenantA, "capocantiere", parentId, body(commessaA, null, "WBS-1", "pianificata", 0))
                .andExpect(status().isOk());
        UUID childId = UUID.randomUUID();
        putActivity(tenantA, "capocantiere", childId, body(commessaA, parentId, "WBS-1.1", "pianificata", 0))
                .andExpect(status().isOk());

        UUID commessaA2 = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerA, tenantA, "Cliente A2", null, null));
            commesse.save(new Commessa(commessaA2, tenantA, customerA, "Muro", null, CommessaStato.PIANIFICATA,
                    "u-a"));
        }));

        // Stesso parentId, ma la commessa cambia: il padre non appartiene più alla nuova commessa.
        putActivity(tenantA, "capocantiere", childId, body(commessaA2, parentId, "WBS-1.1", "pianificata", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCodeInTheSameCommessaIsRejectedAsConflict() throws Exception {
        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, null, "DUP", "pianificata", 0))
                .andExpect(status().isOk());

        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, null, "DUP", "pianificata", 0))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putActivity(tenantA, "capocantiere", id, body(commessaA, null, "DEL-01", "pianificata", 0))
                .andExpect(status().isOk());

        deleteActivity(tenantA, "capocantiere", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/commesse/" + commessaA + "/work-activities").with(TestJwt.user(tenantA, "operaio")))
                .andExpect(jsonPath("$.length()").value(0));
        WorkActivity deleted = TenantContext.callAs(tenantA, () -> activities.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        deleteActivity(tenantA, "capocantiere", id).andExpect(status().isNoContent());
        deleteActivity(tenantA, "capocantiere", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void caposquadraCannotWrite() throws Exception {
        putActivity(tenantA, "caposquadra", UUID.randomUUID(), body(commessaA, null, "X", "pianificata", 0))
                .andExpect(status().isForbidden());
    }

    @Test
    void activitiesOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putActivity(tenantA, "capocantiere", id, body(commessaA, null, "A-01", "pianificata", 0))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA + "/work-activities").with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID customerB = UUID.randomUUID();
        UUID commessaB = UUID.randomUUID();
        TenantContext.runAs(tenantB, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerB, tenantB, "Cliente B", null, null));
            commesse.save(new Commessa(commessaB, tenantB, customerB, "Furto", null, CommessaStato.PIANIFICATA,
                    "u-b"));
        }));
        putActivity(tenantB, "admin", id, body(commessaB, null, "FURTO", "pianificata", 0))
                .andExpect(status().isConflict());
    }

    @Test
    void commessaMustBeActiveAndOfTheSameTenant() throws Exception {
        UUID unknownCommessa = UUID.randomUUID();
        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(unknownCommessa, null, "X", "pianificata", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, null, "X", "non_esiste", 0))
                .andExpect(status().isBadRequest());
        putActivity(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, null, "X", "pianificata", 101))
                .andExpect(status().isBadRequest());
    }
}
