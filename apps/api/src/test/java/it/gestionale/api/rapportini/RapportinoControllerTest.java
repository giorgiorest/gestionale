package it.gestionale.api.rapportini;

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

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.assegnazioni.AssegnazioneRisorsa;
import it.gestionale.api.assegnazioni.AssegnazioneRisorsaRepository;
import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.StatoCommessa;
import it.gestionale.api.lavorazioni.Lavorazione;
import it.gestionale.api.lavorazioni.LavorazioneRepository;
import it.gestionale.api.lavorazioni.StatoLavorazione;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class RapportinoControllerTest extends TestIntegrazioneBase {

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
    RapportinoRepository reports;
    @Autowired
    RisorsaRepository resources;
    @Autowired
    AssegnazioneRisorsaRepository assignments;
    @Autowired
    LavorazioneRepository activities;
    @Autowired
    RigaOreRepository workEntries;
    @Autowired
    SnapshotCostoRigaOreRepository costSnapshots;

    UUID tenantA;
    UUID tenantB;
    UUID commessaA;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        commessaA = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerA, tenantA, "Cliente A", null, null));
            commesse.save(new Commessa(commessaA, tenantA, customerA, "Tetto", null, StatoCommessa.PIANIFICATA,
                    "u-a"));
        }));
    }

    private ResultActions putReport(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/daily-reports/" + id).with(JwtDiTest.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteReport(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/daily-reports/" + id).with(JwtDiTest.user(tenant, role)));
    }

    private ResultActions submitReport(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(post("/api/daily-reports/" + id + "/submit").with(JwtDiTest.user(tenant, role)));
    }

    private ResultActions closeReport(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(post("/api/daily-reports/" + id + "/close").with(JwtDiTest.user(tenant, role)));
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

        mvc.perform(get("/api/daily-reports/" + id).with(JwtDiTest.user(tenantA, "viewer")))
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

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-06-02"));

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports")
                .param("date", "2026-06-01")
                .with(JwtDiTest.user(tenantA, "viewer")))
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

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.length()").value(0));
        Rapportino deleted = ContestoTenant.callAs(tenantA, () -> reports.findByIdAndTenantId(id, tenantA))
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
        mvc.perform(get("/api/daily-reports/" + UUID.randomUUID()).with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isNotFound());
    }

    @Test
    void operaioCannotWrite() throws Exception {
        putReport(tenantA, "operaio", UUID.randomUUID(), body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isForbidden());
    }

    /** Scrittura non ancora limitata alla propria squadra: per ora solo lettura (vedi Ruolo.CAPOSQUADRA). */
    @Test
    void caposquadraCanReadButNotWrite() throws Exception {
        putReport(tenantA, "capocantiere", UUID.randomUUID(), body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(JwtDiTest.user(tenantA, "caposquadra")))
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

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID customerB = UUID.randomUUID();
        UUID commessaB = UUID.randomUUID();
        ContestoTenant.runAs(tenantB, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerB, tenantB, "Cliente B", null, null));
            commesse.save(new Commessa(commessaB, tenantB, customerB, "Furto", null, StatoCommessa.PIANIFICATA,
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
        // Un secondo invio non è un no-op: è un conflitto (vedi Rapportino#submit).
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
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            resources.save(new Risorsa(resourceWithRate, tenantA, "Mario", "Rossi", null, null, "u-a"));
            resources.save(new Risorsa(resourceWithoutRate, tenantA, "Luigi", "Bianchi", null, null, "u-a"));
            assignments.save(new AssegnazioneRisorsa(UUID.randomUUID(), tenantA, resourceWithRate, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", new BigDecimal("20.00"), true, "u-a"));
            assignments.save(new AssegnazioneRisorsa(UUID.randomUUID(), tenantA, resourceWithoutRate, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", null, true, "u-a"));
            activities.save(new Lavorazione(activityId, tenantA, commessaA, null, "ARM-01", "Armatura pilastri",
                    null, StatoLavorazione.PIANIFICATA, 0, "u-a"));
        }));
        putReport(tenantA, "capocantiere", reportId, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());

        UUID entryWithRate = UUID.randomUUID();
        UUID entryWithoutRate = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            workEntries.save(new RigaOre(entryWithRate, tenantA, reportId, resourceWithRate, activityId,
                    new BigDecimal("8"), new BigDecimal("2"), null, "u-a"));
            workEntries.save(new RigaOre(entryWithoutRate, tenantA, reportId, resourceWithoutRate, activityId,
                    new BigDecimal("6"), BigDecimal.ZERO, null, "u-a"));
        }));

        submitReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());
        closeReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());

        List<SnapshotCostoRigaOre> snapshots = ContestoTenant
                .callAs(tenantA, () -> costSnapshots.findByDailyReportIdAndTenantId(reportId, tenantA));
        assertThat(snapshots).hasSize(2);
        SnapshotCostoRigaOre priced = snapshots.stream().filter(s -> s.getWorkEntryId().equals(entryWithRate))
                .findFirst().orElseThrow();
        assertThat(priced.getHourlyCost()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(priced.getLaborCost()).isEqualByComparingTo(new BigDecimal("200.00"));
        SnapshotCostoRigaOre unpriced = snapshots.stream().filter(s -> s.getWorkEntryId().equals(entryWithoutRate))
                .findFirst().orElseThrow();
        assertThat(unpriced.getHourlyCost()).isNull();
        assertThat(unpriced.getLaborCost()).isNull();
    }

    /** Nessun override sull'assegnazione: lo snapshot ricade sulla tariffa oraria base della risorsa (#53). */
    @Test
    void closeFallsBackToTheResourceStandardHourlyCostWhenNoOverrideExists() throws Exception {
        UUID resourceId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            Risorsa resource = new Risorsa(resourceId, tenantA, "Mario", "Rossi", null, null, "u-a");
            resource.updateStandardHourlyCost(new BigDecimal("15.00"), "u-a", java.time.Instant.now());
            resources.save(resource);
            assignments.save(new AssegnazioneRisorsa(UUID.randomUUID(), tenantA, resourceId, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", null, true, "u-a"));
            activities.save(new Lavorazione(activityId, tenantA, commessaA, null, "ARM-01", "Armatura pilastri",
                    null, StatoLavorazione.PIANIFICATA, 0, "u-a"));
        }));
        putReport(tenantA, "capocantiere", reportId, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());
        UUID entryId = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> workEntries.save(new RigaOre(entryId,
                tenantA, reportId, resourceId, activityId, new BigDecimal("8"), BigDecimal.ZERO, null, "u-a"))));
        submitReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());
        closeReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());

        SnapshotCostoRigaOre snapshot = ContestoTenant
                .callAs(tenantA, () -> costSnapshots.findByDailyReportIdAndTenantId(reportId, tenantA)).get(0);
        assertThat(snapshot.getHourlyCost()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(snapshot.getLaborCost()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    /** L'override dell'assegnazione, quando esiste, vince sulla tariffa base della risorsa (#53). */
    @Test
    void assignmentOverrideWinsOverTheResourceStandardHourlyCost() throws Exception {
        UUID resourceId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            Risorsa resource = new Risorsa(resourceId, tenantA, "Mario", "Rossi", null, null, "u-a");
            resource.updateStandardHourlyCost(new BigDecimal("15.00"), "u-a", java.time.Instant.now());
            resources.save(resource);
            assignments.save(new AssegnazioneRisorsa(UUID.randomUUID(), tenantA, resourceId, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", new BigDecimal("25.00"), true, "u-a"));
            activities.save(new Lavorazione(activityId, tenantA, commessaA, null, "ARM-01", "Armatura pilastri",
                    null, StatoLavorazione.PIANIFICATA, 0, "u-a"));
        }));
        putReport(tenantA, "capocantiere", reportId, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());
        UUID entryId = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> workEntries.save(new RigaOre(entryId,
                tenantA, reportId, resourceId, activityId, new BigDecimal("4"), BigDecimal.ZERO, null, "u-a"))));
        submitReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());
        closeReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());

        SnapshotCostoRigaOre snapshot = ContestoTenant
                .callAs(tenantA, () -> costSnapshots.findByDailyReportIdAndTenantId(reportId, tenantA)).get(0);
        assertThat(snapshot.getHourlyCost()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(snapshot.getLaborCost()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    /**
     * Definition of Done della storia #53: chiude un rapportino, poi cambia il costo orario della
     * risorsa, poi rilegge il consuntivo già chiuso — che non deve essere cambiato. Il fallback si
     * applica una sola volta, al momento dello snapshot (vedi {@code RigaOreService.snapshotFor}).
     */
    @Test
    void changingTheStandardHourlyCostAfterCloseDoesNotAlterTheFrozenSnapshot() throws Exception {
        UUID resourceId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            Risorsa resource = new Risorsa(resourceId, tenantA, "Mario", "Rossi", null, null, "u-a");
            resource.updateStandardHourlyCost(new BigDecimal("15.00"), "u-a", java.time.Instant.now());
            resources.save(resource);
            assignments.save(new AssegnazioneRisorsa(UUID.randomUUID(), tenantA, resourceId, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", null, true, "u-a"));
            activities.save(new Lavorazione(activityId, tenantA, commessaA, null, "ARM-01", "Armatura pilastri",
                    null, StatoLavorazione.PIANIFICATA, 0, "u-a"));
        }));
        putReport(tenantA, "capocantiere", reportId, body(commessaA, "2026-06-01", "Sereno"))
                .andExpect(status().isOk());
        UUID entryId = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> workEntries.save(new RigaOre(entryId,
                tenantA, reportId, resourceId, activityId, new BigDecimal("8"), BigDecimal.ZERO, null, "u-a"))));
        submitReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());
        closeReport(tenantA, "capocantiere", reportId).andExpect(status().isOk());

        SnapshotCostoRigaOre beforeChange = ContestoTenant
                .callAs(tenantA, () -> costSnapshots.findByDailyReportIdAndTenantId(reportId, tenantA)).get(0);
        assertThat(beforeChange.getHourlyCost()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(beforeChange.getLaborCost()).isEqualByComparingTo(new BigDecimal("120.00"));

        // Il costo orario della risorsa cambia DOPO la chiusura: il consuntivo già chiuso non deve cambiare.
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            Risorsa resource = resources.findByIdAndTenantId(resourceId, tenantA).orElseThrow();
            resource.updateStandardHourlyCost(new BigDecimal("99.00"), "u-a", java.time.Instant.now());
            resources.save(resource);
        }));

        SnapshotCostoRigaOre afterChange = ContestoTenant
                .callAs(tenantA, () -> costSnapshots.findByDailyReportIdAndTenantId(reportId, tenantA)).get(0);
        assertThat(afterChange.getHourlyCost()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(afterChange.getLaborCost()).isEqualByComparingTo(new BigDecimal("120.00"));
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
        UUID resourceId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            resources.save(new Risorsa(resourceId, tenantA, "Mario", "Rossi", null, null, "u-a"));
            assignments.save(new AssegnazioneRisorsa(UUID.randomUUID(), tenantA, resourceId, commessaA,
                    LocalDate.parse("2026-01-01"), null, "operaio", new BigDecimal("20.00"), true, "u-a"));
            activities.save(new Lavorazione(activityId, tenantA, commessaA, null, "ARM-01", "Armatura pilastri",
                    null, StatoLavorazione.PIANIFICATA, 0, "u-a"));
        }));
        putReport(tenantA, "capocantiere", id, body(commessaA, "2026-06-01", "Sereno")).andExpect(status().isOk());
        UUID entryId = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> workEntries.save(new RigaOre(entryId,
                tenantA, id, resourceId, activityId, new BigDecimal("8"), BigDecimal.ZERO, null, "u-a"))));
        submitReport(tenantA, "capocantiere", id).andExpect(status().isOk());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> attempt = () -> closeReport(tenantA, "capocantiere", id).andReturn();
            List<Future<MvcResult>> results = pool.invokeAll(List.of(attempt, attempt), 30, TimeUnit.SECONDS);
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
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        Rapportino closed = ContestoTenant.callAs(tenantA, () -> reports.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(StatoRapportino.CLOSED);
        assertThat(closed.getVersion()).isEqualTo(2);
        // La riga concorrente perdente non deve aver lasciato un secondo snapshot del costo.
        List<SnapshotCostoRigaOre> snapshots = ContestoTenant
                .callAs(tenantA, () -> costSnapshots.findByDailyReportIdAndTenantId(id, tenantA));
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getLaborCost()).isEqualByComparingTo(new BigDecimal("160.00"));
    }
}
