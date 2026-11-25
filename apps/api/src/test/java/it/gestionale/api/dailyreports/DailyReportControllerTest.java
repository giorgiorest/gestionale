package it.gestionale.api.dailyreports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.CommessaStato;
import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerRepository;
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
import it.gestionale.api.workentries.WorkEntry;
import it.gestionale.api.workentries.WorkEntryCostSnapshot;
import it.gestionale.api.workentries.WorkEntryCostSnapshotRepository;
import it.gestionale.api.workentries.WorkEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class DailyReportControllerTest extends AbstractIntegrationTest {

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
    DailyReportRepository reports;
    @Autowired
    ResourceRepository resources;
    @Autowired
    ResourceAssignmentRepository assignments;
    @Autowired
    WorkActivityRepository activities;
    @Autowired
    WorkEntryRepository workEntries;
    @Autowired
    WorkEntryCostSnapshotRepository costSnapshots;

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

    private ResultActions putReport(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/daily-reports/" + id).with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteReport(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/daily-reports/" + id).with(TestJwt.user(tenant, role)));
    }

    private ResultActions submitReport(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(post("/api/daily-reports/" + id + "/submit").with(TestJwt.user(tenant, role)));
    }

    private ResultActions closeReport(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(post("/api/daily-reports/" + id + "/close").with(TestJwt.user(tenant, role)));
    }

    private String body(UUID commessaId, String date, String weather) {
        return "{\"commessaId\":\"" + commessaId + "\",\"date\":\"" + date + "\",\"weather\":\"" + weather
                + "\",\"notes\":null}";
    }

    @Test
    void capocantiereCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        String json = body(commessaA, "2026-06-01", "Sereno");

        putReport(tenantA, "capocantiere", id, json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putReport(tenantA, "capocantiere", id, json).andExpect(status().isOk());

        String updated = body(commessaA, "2026-06-01", "Pioggia");
        putReport(tenantA, "capocantiere", id, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather").value("Pioggia"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/daily-reports/" + id).with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather").value("Pioggia"));
    }

    @Test
    void onlyOneReportPerCommessaAndDate() throws Exception {
        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());

        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, "2026-06-01", "Nuvoloso"))
                .andExpect(status().isConflict());
    }

    @Test
    void listsByCommessaAndFiltersByDate() throws Exception {
        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());
        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, "2026-06-02", "Pioggia"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-06-02"));

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports")
                .param("date", "2026-06-01")
                .with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].weather").value("Sereno"));
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());

        deleteReport(tenantA, "capocantiere", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.length()").value(0));
        DailyReport deleted = TenantContext.callAs(tenantA, () -> reports.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        deleteReport(tenantA, "capocantiere", id).andExpect(status().isNoContent());
        deleteReport(tenantA, "capocantiere", UUID.randomUUID()).andExpect(status().isNoContent());

        // Un rapportino cancellato può essere ricreato sulla stessa commessa/data.
        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, "2026-06-01", "Rinato"))
                .andExpect(status().isOk());
    }

    @Test
    void getUnknownReportIsNotFound() throws Exception {
        mvc.perform(get("/api/daily-reports/" + UUID.randomUUID()).with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isNotFound());
    }

    @Test
    void operaioCannotWrite() throws Exception {
        putReport(tenantA, "operaio", UUID.randomUUID(), body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isForbidden());
    }

    /** Scrittura non ancora limitata alla propria squadra: per ora solo lettura (vedi Role.CAPOSQUADRA). */
    @Test
    void caposquadraCanReadButNotWrite() throws Exception {
        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(TestJwt.user(tenantA, "caposquadra")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        putReport(tenantA, "caposquadra", UUID.randomUUID(), body(commessaA, "2026-06-02", "Pioggia"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID customerB = UUID.randomUUID();
        UUID commessaB = UUID.randomUUID();
        TenantContext.runAs(tenantB, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerB, tenantB, "Cliente B", null, null));
            commesse.save(new Commessa(commessaB, tenantB, customerB, "Furto", null, CommessaStato.PIANIFICATA,
                    "u-b"));
        }));
        putReport(tenantB, "admin", id, body(commessaB, "2026-06-01", "Furto"))
                .andExpect(status().isConflict());
    }

    @Test
    void commessaMustBeActiveAndOfTheSameTenant() throws Exception {
        UUID unknownCommessa = UUID.randomUUID();
        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(unknownCommessa, "2026-06-01", "Sereno"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putReport(tenantA, "capocantiere", UUID.randomUUID(),
                "{\"commessaId\":\"" + commessaA + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitAndCloseFollowTheWorkflow() throws Exception {
        UUID id = UUID.randomUUID();
        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Sereno")).andExpect(status().isOk());

        submitReport(tenantA, "capocantiere", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andExpect(jsonPath("$.version").value(1));

        closeReport(tenantA, "capocantiere", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void cannotSkipOrRepeatTransitions() throws Exception {
        UUID draft = UUID.randomUUID();
        putReport(tenantA, "capocantiere", draft, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());
        // Non si può chiudere un rapportino ancora in bozza: salta la transizione submit.
        closeReport(tenantA, "capocantiere", draft).andExpect(status().isConflict());

        submitReport(tenantA, "capocantiere", draft).andExpect(status().isOk());
        // Un secondo invio non è un no-op: è un conflitto (vedi DailyReport#submit).
        submitReport(tenantA, "capocantiere", draft).andExpect(status().isConflict());

        closeReport(tenantA, "capocantiere", draft).andExpect(status().isOk());
        // Una seconda chiusura, idem.
        closeReport(tenantA, "capocantiere", draft).andExpect(status().isConflict());
        // Non si può tornare a inviare un rapportino già chiuso.
        submitReport(tenantA, "capocantiere", draft).andExpect(status().isConflict());
    }

    @Test
    void ordinaryEndpointsCannotTouchANonDraftReport() throws Exception {
        UUID id = UUID.randomUUID();
        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Sereno")).andExpect(status().isOk());
        submitReport(tenantA, "capocantiere", id).andExpect(status().isOk());

        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Pioggia"))
                .andExpect(status().isConflict());
        deleteReport(tenantA, "capocantiere", id).andExpect(status().isConflict());
    }

    @Test
    void onlyWriteRoleCanSubmitOrClose() throws Exception {
        UUID id = UUID.randomUUID();
        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Sereno")).andExpect(status().isOk());

        submitReport(tenantA, "viewer", id).andExpect(status().isForbidden());
        submitReport(tenantA, "operaio", id).andExpect(status().isForbidden());
        submitReport(tenantA, "capocantiere", id).andExpect(status().isOk());
        closeReport(tenantA, "viewer", id).andExpect(status().isForbidden());
    }

    @Test
    void closeSnapshotsLaborCostAndFlagsResourcesWithoutAKnownRate() throws Exception {
        UUID resourceWithRate = UUID.randomUUID();
        UUID resourceWithoutRate = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            resources.save(new Resource(resourceWithRate, tenantA, "Mario", "Rossi", null, null, "u-a"));
            resources.save(new Resource(resourceWithoutRate, tenantA, "Luigi", "Bianchi", null, null, "u-a"));
            assignments.save(new ResourceAssignment(UUID.randomUUID(), tenantA, resourceWithRate, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", new BigDecimal("20.00"), true, "u-a"));
            assignments.save(new ResourceAssignment(UUID.randomUUID(), tenantA, resourceWithoutRate, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", null, true, "u-a"));
            activities.save(new WorkActivity(activityId, tenantA, commessaA, null, "ARM-01", "Armatura pilastri",
                    null, WorkActivityStatus.PIANIFICATA, 0, "u-a"));
        }));
        putReport(tenantA, "capocantiere", reportId, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());

        UUID entryWithRate = UUID.randomUUID();
        UUID entryWithoutRate = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            workEntries.save(new WorkEntry(entryWithRate, tenantA, reportId, resourceWithRate, activityId,
                    new BigDecimal("8"), new BigDecimal("2"), null, "u-a"));
            workEntries.save(new WorkEntry(entryWithoutRate, tenantA, reportId, resourceWithoutRate, activityId,
                    new BigDecimal("6"), BigDecimal.ZERO, null, "u-a"));
        }));

        submitReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());
        closeReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());

        List<WorkEntryCostSnapshot> snapshots = TenantContext
                .callAs(tenantA, () -> costSnapshots.findByDailyReportIdAndTenantId(reportId, tenantA));
        assertThat(snapshots).hasSize(2);
        WorkEntryCostSnapshot priced = snapshots.stream().filter(s -> s.getWorkEntryId().equals(entryWithRate))
                .findFirst().orElseThrow();
        assertThat(priced.getHourlyCost()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(priced.getLaborCost()).isEqualByComparingTo(new BigDecimal("200.00"));
        WorkEntryCostSnapshot unpriced = snapshots.stream().filter(s -> s.getWorkEntryId().equals(entryWithoutRate))
                .findFirst().orElseThrow();
        assertThat(unpriced.getHourlyCost()).isNull();
        assertThat(unpriced.getLaborCost()).isNull();
    }

    /**
     * Due richieste di chiusura concorrenti sullo stesso rapportino: una sola deve avere
     * successo. L'altra fallisce con 409 — per il controllo applicativo sullo stato (se legge il
     * rapportino dopo che l'altra ha già chiuso) o per il blocco ottimistico @Version (se le due
     * leggono lo stesso stato prima che una delle due vada a buon fine) — mai con uno stato
     * incoerente o due snapshot del costo per lo stesso rapportino.
     */
    @Test
    void concurrentCloseRequestsOnlyOneSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Sereno")).andExpect(status().isOk());
        submitReport(tenantA, "capocantiere", id).andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> attempt = () -> closeReport(tenantA, "capocantiere", id).andReturn();
            List<Future<MvcResult>> results = pool.invokeAll(List.of(attempt, attempt));
            List<Integer> statuses = results.stream().map(f -> {
                try {
                    return f.get().getResponse().getStatus();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).sorted().toList();
            assertThat(statuses).containsExactly(200, 409);
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        DailyReport closed = TenantContext.callAs(tenantA, () -> reports.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(DailyReportStatus.CLOSED);
        assertThat(closed.getVersion()).isEqualTo(2);
    }
}
