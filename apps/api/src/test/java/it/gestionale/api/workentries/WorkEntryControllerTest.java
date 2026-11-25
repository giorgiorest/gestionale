package it.gestionale.api.workentries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import it.gestionale.api.dailyreports.DailyReport;
import it.gestionale.api.dailyreports.DailyReportRepository;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.resourceassignments.ResourceAssignment;
import it.gestionale.api.resourceassignments.ResourceAssignmentRepository;
import it.gestionale.api.resources.Resource;
import it.gestionale.api.resources.ResourceRepository;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantRepository;
import it.gestionale.api.workactivities.WorkActivity;
import it.gestionale.api.workactivities.WorkActivityRepository;
import it.gestionale.api.workactivities.WorkActivityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class WorkEntryControllerTest extends AbstractIntegrationTest {

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
    ResourceRepository resources;
    @Autowired
    ResourceAssignmentRepository assignments;
    @Autowired
    WorkActivityRepository activities;
    @Autowired
    DailyReportRepository reports;
    @Autowired
    WorkEntryRepository entries;

    UUID tenantA;
    UUID tenantB;
    UUID commessaA;
    UUID resourceA;
    UUID activityA;
    UUID reportA;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        commessaA = UUID.randomUUID();
        resourceA = UUID.randomUUID();
        activityA = UUID.randomUUID();
        reportA = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerA, tenantA, "Cliente A", null, null));
            commesse.save(new Commessa(commessaA, tenantA, customerA, "Tetto", null, CommessaStato.PIANIFICATA,
                    "u-a"));
            resources.save(new Resource(resourceA, tenantA, "Mario", "Rossi", null, null, "u-a"));
            assignments.save(new ResourceAssignment(UUID.randomUUID(), tenantA, resourceA, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", null, true, "u-a"));
            activities.save(new WorkActivity(activityA, tenantA, commessaA, null, "ARM-01", "Armatura pilastri",
                    null, WorkActivityStatus.PIANIFICATA, 0, "u-a"));
            reports.save(new DailyReport(reportA, tenantA, commessaA, LocalDate.parse("2026-06-01"), "Sereno", null,
                    "u-a"));
        }));
    }

    private ResultActions putEntry(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/work-entries/" + id).with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteEntry(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/work-entries/" + id).with(TestJwt.user(tenant, role)));
    }

    private String body(UUID dailyReportId, UUID resourceId, UUID workActivityId, String hours,
            String overtimeHours) {
        return "{\"dailyReportId\":\"" + dailyReportId + "\",\"resourceId\":\"" + resourceId
                + "\",\"workActivityId\":\"" + workActivityId + "\",\"hours\":" + hours + ",\"overtimeHours\":"
                + overtimeHours + ",\"notes\":null}";
    }

    @Test
    void capocantiereCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        String json = body(reportA, resourceA, activityA, "8", "0");

        putEntry(tenantA, "capocantiere", id, json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.hours").value(8))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putEntry(tenantA, "capocantiere", id, json).andExpect(status().isOk());

        String updated = body(reportA, resourceA, activityA, "6", "2");
        putEntry(tenantA, "capocantiere", id, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(6))
                .andExpect(jsonPath("$.overtimeHours").value(2))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/daily-reports/" + reportA + "/work-entries").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hours").value(6));
    }

    @Test
    void resourceMustHaveAValidAssignmentToTheCommessaOnTheReportDate() throws Exception {
        UUID unassignedResource = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s ->
                resources.save(new Resource(unassignedResource, tenantA, "Luigi", "Bianchi", null, null, "u-a"))));

        putEntry(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, unassignedResource, activityA, "8", "0"))
                .andExpect(status().isBadRequest());

        // Assegnata, ma con periodo che non comprende la data del rapportino (2026-06-01).
        UUID laterResource = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            resources.save(new Resource(laterResource, tenantA, "Anna", "Verdi", null, null, "u-a"));
            assignments.save(new ResourceAssignment(UUID.randomUUID(), tenantA, laterResource, commessaA,
                    LocalDate.parse("2026-07-01"), null, "operaio", null, true, "u-a"));
        }));
        putEntry(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, laterResource, activityA, "8", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workActivityMustBelongToTheSameCommessaAsTheReport() throws Exception {
        UUID customerA2 = UUID.randomUUID();
        UUID commessaA2 = UUID.randomUUID();
        UUID otherActivity = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerA2, tenantA, "Cliente A2", null, null));
            commesse.save(new Commessa(commessaA2, tenantA, customerA2, "Muro", null, CommessaStato.PIANIFICATA,
                    "u-a"));
            activities.save(new WorkActivity(otherActivity, tenantA, commessaA2, null, "MUR-01", "Muratura", null,
                    WorkActivityStatus.PIANIFICATA, 0, "u-a"));
        }));

        putEntry(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, resourceA, otherActivity, "8", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putEntry(tenantA, "capocantiere", id, body(reportA, resourceA, activityA, "8", "0"))
                .andExpect(status().isOk());

        deleteEntry(tenantA, "capocantiere", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/daily-reports/" + reportA + "/work-entries").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.length()").value(0));
        WorkEntry deleted = TenantContext.callAs(tenantA, () -> entries.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        deleteEntry(tenantA, "capocantiere", id).andExpect(status().isNoContent());
        deleteEntry(tenantA, "capocantiere", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void caposquadraCanReadButNotWrite() throws Exception {
        putEntry(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, resourceA, activityA, "8", "0"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/daily-reports/" + reportA + "/work-entries").with(TestJwt.user(tenantA, "caposquadra")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        putEntry(tenantA, "caposquadra", UUID.randomUUID(), body(reportA, resourceA, activityA, "4", "0"))
                .andExpect(status().isForbidden());
    }

    /** Nessun permesso finché la scrittura "proprie righe" non è una scelta presa (vedi Role.OPERAIO). */
    @Test
    void operaioHasNoPermissionAtAll() throws Exception {
        mvc.perform(get("/api/daily-reports/" + reportA + "/work-entries").with(TestJwt.user(tenantA, "operaio")))
                .andExpect(status().isForbidden());
        putEntry(tenantA, "operaio", UUID.randomUUID(), body(reportA, resourceA, activityA, "8", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void entriesOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putEntry(tenantA, "capocantiere", id, body(reportA, resourceA, activityA, "8", "0"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/daily-reports/" + reportA + "/work-entries").with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID customerB = UUID.randomUUID();
        UUID commessaB = UUID.randomUUID();
        UUID resourceB = UUID.randomUUID();
        UUID activityB = UUID.randomUUID();
        UUID reportB = UUID.randomUUID();
        TenantContext.runAs(tenantB, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerB, tenantB, "Cliente B", null, null));
            commesse.save(new Commessa(commessaB, tenantB, customerB, "Furto", null, CommessaStato.PIANIFICATA,
                    "u-b"));
            resources.save(new Resource(resourceB, tenantB, "Luigi", "Bianchi", null, null, "u-b"));
            assignments.save(new ResourceAssignment(UUID.randomUUID(), tenantB, resourceB, commessaB,
                    LocalDate.parse("2026-01-01"), null, "operaio", null, true, "u-b"));
            activities.save(new WorkActivity(activityB, tenantB, commessaB, null, "FUR-01", "Furto", null,
                    WorkActivityStatus.PIANIFICATA, 0, "u-b"));
            reports.save(new DailyReport(reportB, tenantB, commessaB, LocalDate.parse("2026-06-01"), "Sereno", null,
                    "u-b"));
        }));
        putEntry(tenantB, "admin", id, body(reportB, resourceB, activityB, "8", "0"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        // Ore negative.
        putEntry(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, resourceA, activityA, "-1", "0"))
                .andExpect(status().isBadRequest());
        putEntry(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, resourceA, activityA, "8", "-1"))
                .andExpect(status().isBadRequest());
    }

    /** Il rapportino collegato non più DRAFT blocca update/delete della riga (#51). */
    @Test
    void cannotUpdateOrDeleteAnEntryWhenTheReportIsNoLongerDraft() throws Exception {
        UUID id = UUID.randomUUID();
        putEntry(tenantA, "capocantiere", id, body(reportA, resourceA, activityA, "8", "0"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/daily-reports/" + reportA + "/submit").with(TestJwt.user(tenantA, "capocantiere")))
                .andExpect(status().isOk());

        putEntry(tenantA, "capocantiere", id, body(reportA, resourceA, activityA, "6", "0"))
                .andExpect(status().isConflict());
        deleteEntry(tenantA, "capocantiere", id).andExpect(status().isConflict());
        // Nemmeno una riga nuova si può aggiungere a un rapportino non più DRAFT.
        putEntry(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, resourceA, activityA, "4", "0"))
                .andExpect(status().isConflict());
    }
}
