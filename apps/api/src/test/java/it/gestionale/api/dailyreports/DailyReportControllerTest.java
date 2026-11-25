package it.gestionale.api.dailyreports;

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

        mvc.perform(get("/api/daily-reports/" + id).with(TestJwt.user(tenantA, "operaio")))
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

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(TestJwt.user(tenantA, "operaio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-06-02"));

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports")
                .param("date", "2026-06-01")
                .with(TestJwt.user(tenantA, "operaio")))
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

        mvc.perform(get("/api/commesse/" + commessaA + "/daily-reports").with(TestJwt.user(tenantA, "operaio")))
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
        mvc.perform(get("/api/daily-reports/" + UUID.randomUUID()).with(TestJwt.user(tenantA, "operaio")))
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
}
