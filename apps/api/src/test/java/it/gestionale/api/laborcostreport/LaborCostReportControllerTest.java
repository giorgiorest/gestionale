package it.gestionale.api.laborcostreport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * KPI costo manodopera (#53): legge {@code work_entry_cost_snapshots}, non le righe grezze — gli
 * snapshot sono inseriti direttamente, senza passare dal workflow submit/close, perché la query
 * non torna a verificare lo stato del rapportino (solo la sua esistenza/periodo/commessa). Serve
 * comunque una {@code WorkEntry} reale per ogni snapshot: {@code work_entry_id} è una chiave
 * esterna vera (V13), a differenza degli altri riferimenti di questo test.
 */
@AutoConfigureMockMvc
class LaborCostReportControllerTest extends AbstractIntegrationTest {

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
    WorkActivityRepository activities;
    @Autowired
    DailyReportRepository reports;
    @Autowired
    WorkEntryRepository workEntries;
    @Autowired
    WorkEntryCostSnapshotRepository snapshots;

    UUID tenantA;
    UUID commessaA;
    UUID activityX;
    UUID activityY;
    UUID reportJan5;
    UUID reportJan12;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        commessaA = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        UUID resourceA = UUID.randomUUID();
        activityX = UUID.randomUUID();
        activityY = UUID.randomUUID();
        reportJan5 = UUID.randomUUID();
        reportJan12 = UUID.randomUUID();
        UUID reportFeb2 = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerA, tenantA, "Cliente A", null, null));
            commesse.save(new Commessa(commessaA, tenantA, customerA, "Tetto", null, CommessaStato.PIANIFICATA,
                    "u-a"));
            resources.save(new Resource(resourceA, tenantA, "Mario", "Rossi", null, null, "u-a"));
            activities.save(new WorkActivity(activityX, tenantA, commessaA, null, "ARM-01", "Armatura", null,
                    WorkActivityStatus.PIANIFICATA, 0, "u-a"));
            activities.save(new WorkActivity(activityY, tenantA, commessaA, null, "MUR-01", "Muratura", null,
                    WorkActivityStatus.PIANIFICATA, 0, "u-a"));
            reports.save(new DailyReport(reportJan5, tenantA, commessaA, LocalDate.parse("2026-01-05"), null, null,
                    "u-a"));
            reports.save(new DailyReport(reportJan12, tenantA, commessaA, LocalDate.parse("2026-01-12"), null, null,
                    "u-a"));
            reports.save(new DailyReport(reportFeb2, tenantA, commessaA, LocalDate.parse("2026-02-02"), null, null,
                    "u-a"));
            // Il vincolo di chiave esterna di work_entry_cost_snapshots (V13) richiede una WorkEntry
            // reale per ogni snapshot: non basta un id a caso come per gli altri riferimenti del test.
            UUID entryJan5X = UUID.randomUUID();
            UUID entryJan12X = UUID.randomUUID();
            UUID entryJan5Y = UUID.randomUUID();
            UUID entryFeb2X = UUID.randomUUID();
            workEntries.save(new WorkEntry(entryJan5X, tenantA, reportJan5, resourceA, activityX,
                    new BigDecimal("8"), BigDecimal.ZERO, null, "u-a"));
            workEntries.save(new WorkEntry(entryJan12X, tenantA, reportJan12, resourceA, activityX,
                    new BigDecimal("4"), BigDecimal.ZERO, null, "u-a"));
            workEntries.save(new WorkEntry(entryJan5Y, tenantA, reportJan5, resourceA, activityY,
                    new BigDecimal("6"), BigDecimal.ZERO, null, "u-a"));
            workEntries.save(new WorkEntry(entryFeb2X, tenantA, reportFeb2, resourceA, activityX,
                    new BigDecimal("5"), BigDecimal.ZERO, null, "u-a"));
            // activityX, 5/1: costo noto (8h a 20.00).
            snapshots.save(new WorkEntryCostSnapshot(UUID.randomUUID(), tenantA, reportJan5, entryJan5X,
                    resourceA, activityX, new BigDecimal("8"), BigDecimal.ZERO, new BigDecimal("20.00"),
                    new BigDecimal("160.00")));
            // activityX, 12/1: costo sconosciuto (nessuna tariffa nota per quella riga).
            snapshots.save(new WorkEntryCostSnapshot(UUID.randomUUID(), tenantA, reportJan12, entryJan12X,
                    resourceA, activityX, new BigDecimal("4"), BigDecimal.ZERO, null, null));
            // activityY, 5/1: costo noto (6h a 10.00).
            snapshots.save(new WorkEntryCostSnapshot(UUID.randomUUID(), tenantA, reportJan5, entryJan5Y,
                    resourceA, activityY, new BigDecimal("6"), BigDecimal.ZERO, new BigDecimal("10.00"),
                    new BigDecimal("60.00")));
            // Fuori dall'intervallo interrogato (febbraio): non deve comparire.
            snapshots.save(new WorkEntryCostSnapshot(UUID.randomUUID(), tenantA, reportFeb2, entryFeb2X,
                    resourceA, activityX, new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("20.00"),
                    new BigDecimal("100.00")));
        }));
    }

    private ResultActions getReport(UUID tenant, String role, UUID commessaId, String from, String to)
            throws Exception {
        return mvc.perform(get("/api/commesse/" + commessaId + "/reports/labor-cost").param("from", from)
                .param("to", to).with(TestJwt.user(tenant, role)));
    }

    @Test
    void aggregatesHoursAndKnownCostByWorkActivity() throws Exception {
        getReport(tenantA, "admin", commessaA, "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(18))
                .andExpect(jsonPath("$.totalCost").value(220.00))
                .andExpect(jsonPath("$.byWorkActivity.length()").value(2))
                .andExpect(jsonPath("$.byWorkActivity[?(@.workActivityId=='" + activityX + "')].hours").value(12.0))
                .andExpect(jsonPath("$.byWorkActivity[?(@.workActivityId=='" + activityX + "')].cost").value(160.00))
                .andExpect(jsonPath("$.byWorkActivity[?(@.workActivityId=='" + activityY + "')].hours").value(6.0))
                .andExpect(jsonPath("$.byWorkActivity[?(@.workActivityId=='" + activityY + "')].cost").value(60.00));
    }

    @Test
    void entriesOutsideTheRangeOrOfAnotherCommessaAreExcluded() throws Exception {
        getReport(tenantA, "admin", commessaA, "2026-02-01", "2026-02-28")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(5))
                .andExpect(jsonPath("$.totalCost").value(100.00))
                .andExpect(jsonPath("$.byWorkActivity.length()").value(1));

        UUID customerA2 = UUID.randomUUID();
        UUID commessaA2 = UUID.randomUUID();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Customer(customerA2, tenantA, "Cliente A2", null, null));
            commesse.save(new Commessa(commessaA2, tenantA, customerA2, "Muro", null, CommessaStato.PIANIFICATA,
                    "u-a"));
        }));
        getReport(tenantA, "admin", commessaA2, "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(0))
                .andExpect(jsonPath("$.totalCost").doesNotExist())
                .andExpect(jsonPath("$.byWorkActivity.length()").value(0));
    }

    @Test
    void rejectsAnInvertedRange() throws Exception {
        getReport(tenantA, "admin", commessaA, "2026-01-31", "2026-01-01").andExpect(status().isBadRequest());
    }

    @Test
    void onlyRolesWithLaborCostReadCanReadTheReport() throws Exception {
        for (String role : new String[] { "operator", "viewer", "capocantiere" }) {
            getReport(tenantA, role, commessaA, "2026-01-01", "2026-01-31").andExpect(status().isOk());
        }
        for (String role : new String[] { "caposquadra", "operaio", "amministrativo" }) {
            getReport(tenantA, role, commessaA, "2026-01-01", "2026-01-31").andExpect(status().isForbidden());
        }
    }

    @Test
    void aCommessaOfAnotherTenantIsNotVisible() throws Exception {
        UUID tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        getReport(tenantB, "admin", commessaA, "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(0));
    }
}
