package it.gestionale.api.rapportini;

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

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.StatoCommessa;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class MaterialeRapportinoControllerTest extends TestIntegrazioneBase {

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
    MaterialeRapportinoRepository materials;

    UUID tenantA;
    UUID tenantB;
    UUID commessaA;
    UUID reportA;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        commessaA = UUID.randomUUID();
        reportA = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerA, tenantA, "Cliente A", null, null));
            commesse.save(new Commessa(commessaA, tenantA, customerA, "Tetto", null, StatoCommessa.PIANIFICATA,
                    "u-a"));
            reports.save(new Rapportino(reportA, tenantA, commessaA, LocalDate.parse("2026-06-01"), "Sereno", null,
                    "u-a"));
        }));
    }

    private ResultActions putMaterial(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/daily-report-materials/" + id).with(JwtDiTest.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteMaterial(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/daily-report-materials/" + id).with(JwtDiTest.user(tenant, role)));
    }

    private String body(UUID dailyReportId, String description, String quantity, String unit) {
        return "{\"dailyReportId\":\"" + dailyReportId + "\",\"description\":\"" + description + "\",\"quantity\":"
                + quantity + ",\"unit\":\"" + unit + "\",\"notes\":null}";
    }

    @Test
    void capocantiereCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        String json = body(reportA, "Cemento", "10", "sacchi");

        putMaterial(tenantA, "capocantiere", id, json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Cemento"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putMaterial(tenantA, "capocantiere", id, json).andExpect(status().isOk());

        String updated = body(reportA, "Cemento", "6", "sacchi");
        putMaterial(tenantA, "capocantiere", id, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/daily-reports/" + reportA + "/materials").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(6));
    }

    @Test
    void dailyReportMustBeValidAndFromTheSameTenant() throws Exception {
        putMaterial(tenantA, "capocantiere", UUID.randomUUID(), body(UUID.randomUUID(), "Cemento", "10", "sacchi"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putMaterial(tenantA, "capocantiere", id, body(reportA, "Cemento", "10", "sacchi"))
                .andExpect(status().isOk());

        deleteMaterial(tenantA, "capocantiere", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/daily-reports/" + reportA + "/materials").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.length()").value(0));
        MaterialeRapportino deleted = ContestoTenant.callAs(tenantA, () -> materials.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        deleteMaterial(tenantA, "capocantiere", id).andExpect(status().isNoContent());
        deleteMaterial(tenantA, "capocantiere", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void caposquadraCanReadButNotWrite() throws Exception {
        putMaterial(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, "Cemento", "10", "sacchi"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/daily-reports/" + reportA + "/materials").with(JwtDiTest.user(tenantA, "caposquadra")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        putMaterial(tenantA, "caposquadra", UUID.randomUUID(), body(reportA, "Sabbia", "4", "mc"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operaioHasNoPermissionAtAll() throws Exception {
        mvc.perform(get("/api/daily-reports/" + reportA + "/materials").with(JwtDiTest.user(tenantA, "operaio")))
                .andExpect(status().isForbidden());
        putMaterial(tenantA, "operaio", UUID.randomUUID(), body(reportA, "Cemento", "10", "sacchi"))
                .andExpect(status().isForbidden());
    }

    @Test
    void materialsOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putMaterial(tenantA, "capocantiere", id, body(reportA, "Cemento", "10", "sacchi"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/daily-reports/" + reportA + "/materials").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID customerB = UUID.randomUUID();
        UUID commessaB = UUID.randomUUID();
        UUID reportB = UUID.randomUUID();
        ContestoTenant.runAs(tenantB, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerB, tenantB, "Cliente B", null, null));
            commesse.save(new Commessa(commessaB, tenantB, customerB, "Furto", null, StatoCommessa.PIANIFICATA,
                    "u-b"));
            reports.save(new Rapportino(reportB, tenantB, commessaB, LocalDate.parse("2026-06-01"), "Sereno", null,
                    "u-b"));
        }));
        putMaterial(tenantB, "admin", id, body(reportB, "Cemento", "10", "sacchi"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        // Quantità non positiva.
        putMaterial(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, "Cemento", "0", "sacchi"))
                .andExpect(status().isBadRequest());
        putMaterial(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, "Cemento", "-1", "sacchi"))
                .andExpect(status().isBadRequest());
        // Descrizione mancante.
        putMaterial(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, "", "10", "sacchi"))
                .andExpect(status().isBadRequest());
    }

    /** Il rapportino collegato non più DRAFT blocca update/delete della riga (#51). */
    @Test
    void cannotUpdateOrDeleteAMaterialWhenTheReportIsNoLongerDraft() throws Exception {
        UUID id = UUID.randomUUID();
        putMaterial(tenantA, "capocantiere", id, body(reportA, "Cemento", "10", "sacchi"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/daily-reports/" + reportA + "/submit").with(JwtDiTest.user(tenantA, "capocantiere")))
                .andExpect(status().isOk());

        putMaterial(tenantA, "capocantiere", id, body(reportA, "Cemento", "6", "sacchi"))
                .andExpect(status().isConflict());
        deleteMaterial(tenantA, "capocantiere", id).andExpect(status().isConflict());
        // Nemmeno una riga nuova si può aggiungere a un rapportino non più DRAFT.
        putMaterial(tenantA, "capocantiere", UUID.randomUUID(), body(reportA, "Sabbia", "4", "mc"))
                .andExpect(status().isConflict());
    }
}
