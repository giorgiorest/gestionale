package it.gestionale.api.risorse;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class RisorsaControllerTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;
    @Autowired
    RisorsaRepository resources;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions putResource(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/resources/" + id).with(JwtDiTest.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteResource(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/resources/" + id).with(JwtDiTest.user(tenant, role)));
    }

    private Risorsa row(UUID tenant, UUID id) {
        return ContestoTenant.callAs(tenant, () -> resources.findByIdAndTenantId(id, tenant)).orElseThrow();
    }

    @Test
    void capocantiereCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();

        putResource(tenantA, "capocantiere", id, "{\"firstName\":\"Mario\",\"lastName\":\"Verdi\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putResource(tenantA, "capocantiere", id, "{\"firstName\":\"Mario\",\"lastName\":\"Verdi\"}")
                .andExpect(status().isOk());
        putResource(tenantA, "capocantiere", id, "{\"firstName\":\"Mario\",\"lastName\":\"Bianchi\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Bianchi"))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/resources").with(JwtDiTest.user(tenantA, "operaio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastName").value("Bianchi"));
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putResource(tenantA, "capocantiere", id, "{\"firstName\":\"Da\",\"lastName\":\"Cancellare\"}")
                .andExpect(status().isOk());

        deleteResource(tenantA, "capocantiere", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/resources").with(JwtDiTest.user(tenantA, "caposquadra")))
                .andExpect(jsonPath("$.length()").value(0));
        Risorsa deleted = row(tenantA, id);
        assertThat(deleted.isDeleted()).isTrue();

        // Reinvio dalla outbox: nessun errore, nessun cambiamento.
        deleteResource(tenantA, "capocantiere", id).andExpect(status().isNoContent());
        assertThat(row(tenantA, id).getDeletedAt()).isEqualTo(deleted.getDeletedAt());

        // Id mai esistito: la outbox deve poter svuotarsi comunque.
        deleteResource(tenantA, "capocantiere", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void operaioCannotWrite() throws Exception {
        putResource(tenantA, "operaio", UUID.randomUUID(), "{\"firstName\":\"X\",\"lastName\":\"Y\"}")
                .andExpect(status().isForbidden());
        deleteResource(tenantA, "operaio", UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    void resourcesOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putResource(tenantA, "capocantiere", id, "{\"firstName\":\"Anna\",\"lastName\":\"Di A\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/resources").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        // Stesso id da un altro tenant: la riga non è visibile e l'inserimento va in conflitto.
        putResource(tenantB, "admin", id, "{\"firstName\":\"Furto\",\"lastName\":\"Furto\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void teamLeadMustBeAnActiveResourceOfTheSameTenant() throws Exception {
        UUID unknown = UUID.randomUUID();
        putResource(tenantA, "capocantiere", UUID.randomUUID(),
                "{\"firstName\":\"Mario\",\"lastName\":\"Verdi\",\"teamLeadId\":\"" + unknown + "\"}")
                .andExpect(status().isBadRequest());

        UUID leadOfB = UUID.randomUUID();
        putResource(tenantB, "admin", leadOfB, "{\"firstName\":\"Capo\",\"lastName\":\"Di B\"}")
                .andExpect(status().isOk());
        putResource(tenantA, "capocantiere", UUID.randomUUID(),
                "{\"firstName\":\"Mario\",\"lastName\":\"Verdi\",\"teamLeadId\":\"" + leadOfB + "\"}")
                .andExpect(status().isBadRequest());

        UUID leadOfA = UUID.randomUUID();
        putResource(tenantA, "capocantiere", leadOfA, "{\"firstName\":\"Capo\",\"lastName\":\"Di A\"}")
                .andExpect(status().isOk());
        UUID id = UUID.randomUUID();
        putResource(tenantA, "capocantiere", id,
                "{\"firstName\":\"Mario\",\"lastName\":\"Verdi\",\"teamLeadId\":\"" + leadOfA + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamLeadId").value(leadOfA.toString()));
    }

    @Test
    void teamsAreOnlyOneLevelDeep() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        putResource(tenantA, "capocantiere", a, "{\"firstName\":\"A\",\"lastName\":\"A\"}").andExpect(status().isOk());
        putResource(tenantA, "capocantiere", b,
                "{\"firstName\":\"B\",\"lastName\":\"B\",\"teamLeadId\":\"" + a + "\"}")
                .andExpect(status().isOk());

        // B è già membro della squadra di A: non può a sua volta avere un capo squadra proprio.
        putResource(tenantA, "capocantiere", c,
                "{\"firstName\":\"C\",\"lastName\":\"C\",\"teamLeadId\":\"" + b + "\"}")
                .andExpect(status().isBadRequest());

        // A è già capo squadra (di B): non può diventare membro di un'altra squadra.
        UUID d = UUID.randomUUID();
        putResource(tenantA, "capocantiere", d, "{\"firstName\":\"D\",\"lastName\":\"D\"}").andExpect(status().isOk());
        putResource(tenantA, "capocantiere", a,
                "{\"firstName\":\"A\",\"lastName\":\"A\",\"teamLeadId\":\"" + d + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putResource(tenantA, "capocantiere", UUID.randomUUID(), "{\"firstName\":\" \",\"lastName\":\"Verdi\"}")
                .andExpect(status().isBadRequest());
    }
}
