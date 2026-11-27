package it.gestionale.api.commesse;

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
class CommessaControllerTest extends TestIntegrazioneBase {

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

    UUID tenantA;
    UUID tenantB;
    UUID customerA;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        customerA = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(
                s -> customers.save(new Cliente(customerA, tenantA, "Cliente A", null, null))));
    }

    private ResultActions putCommessa(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/commesse/" + id).with(JwtDiTest.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteCommessa(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/commesse/" + id).with(JwtDiTest.user(tenant, role)));
    }

    private Commessa row(UUID tenant, UUID id) {
        return ContestoTenant.callAs(tenant, () -> commesse.findByIdAndTenantId(id, tenant)).orElseThrow();
    }

    @Test
    void amministrativoCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"stato\":\"pianificata\"}";

        putCommessa(tenantA, "amministrativo", id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.stato").value("pianificata"))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putCommessa(tenantA, "amministrativo", id, body).andExpect(status().isOk());

        String updated = "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"indirizzo\":\"Via Roma 1\",\"stato\":\"in_corso\"}";
        putCommessa(tenantA, "amministrativo", id, updated)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indirizzo").value("Via Roma 1"))
                .andExpect(jsonPath("$.stato").value("in_corso"))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/commesse").with(JwtDiTest.user(tenantA, "operaio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stato").value("in_corso"));
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Da cancellare\",\"stato\":\"pianificata\"}")
                .andExpect(status().isOk());

        deleteCommessa(tenantA, "amministrativo", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/commesse").with(JwtDiTest.user(tenantA, "capocantiere")))
                .andExpect(jsonPath("$.length()").value(0));
        Commessa deleted = row(tenantA, id);
        assertThat(deleted.isDeleted()).isTrue();

        deleteCommessa(tenantA, "amministrativo", id).andExpect(status().isNoContent());
        assertThat(row(tenantA, id).getDeletedAt()).isEqualTo(deleted.getDeletedAt());

        deleteCommessa(tenantA, "amministrativo", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void operaioCannotWrite() throws Exception {
        putCommessa(tenantA, "operaio", UUID.randomUUID(),
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"X\",\"stato\":\"pianificata\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void capocantiereCanUpdateTheStatoOfTheCommessa() throws Exception {
        UUID id = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"stato\":\"pianificata\"}")
                .andExpect(status().isOk());

        putCommessa(tenantA, "capocantiere", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"stato\":\"in_corso\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("in_corso"));
    }

    @Test
    void commesseOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Di A\",\"stato\":\"pianificata\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/commesse").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        // Il cliente del tentativo è di B (un attacco reale userebbe un proprio riferimento valido,
        // non quello di A, invisibile a B): isola il conflitto sull'id da un eventuale 400 sul cliente.
        UUID customerB = UUID.randomUUID();
        ContestoTenant.runAs(tenantB, () -> tx.executeWithoutResult(
                s -> customers.save(new Cliente(customerB, tenantB, "Cliente B", null, null))));
        putCommessa(tenantB, "admin", id,
                "{\"customerId\":\"" + customerB + "\",\"nome\":\"Furto\",\"stato\":\"pianificata\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void customerMustBeAnActiveCustomerOfTheSameTenant() throws Exception {
        UUID unknown = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + unknown + "\",\"nome\":\"X\",\"stato\":\"pianificata\"}")
                .andExpect(status().isBadRequest());

        UUID customerB = UUID.randomUUID();
        ContestoTenant.runAs(tenantB, () -> tx.executeWithoutResult(
                s -> customers.save(new Cliente(customerB, tenantB, "Cliente B", null, null))));
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + customerB + "\",\"nome\":\"X\",\"stato\":\"pianificata\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCustomerDeletedAfterwardsDoesNotBlockFurtherUpdatesToItsCommesse() throws Exception {
        UUID id = UUID.randomUUID();
        putCommessa(tenantA, "amministrativo", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"stato\":\"pianificata\"}")
                .andExpect(status().isOk());

        // Il cliente se ne va a metà cantiere: la commessa resta, collegata a un cliente cancellato.
        mvc.perform(delete("/api/customers/" + customerA).with(JwtDiTest.user(tenantA, "admin")))
                .andExpect(status().isNoContent());

        // Chiudere il cantiere (stesso cliente, non cambia) deve restare possibile.
        putCommessa(tenantA, "capocantiere", id,
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Tetto\",\"stato\":\"conclusa\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("conclusa"));

        // Una nuova commessa non può invece agganciarsi a un cliente già cancellato.
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"Altra\",\"stato\":\"pianificata\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + customerA + "\",\"nome\":\" \",\"stato\":\"pianificata\"}")
                .andExpect(status().isBadRequest());
        putCommessa(tenantA, "amministrativo", UUID.randomUUID(),
                "{\"customerId\":\"" + customerA + "\",\"nome\":\"X\",\"stato\":\"non_esiste\"}")
                .andExpect(status().isBadRequest());
    }
}
