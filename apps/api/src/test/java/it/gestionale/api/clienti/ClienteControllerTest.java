package it.gestionale.api.clienti;

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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class ClienteControllerTest extends TestIntegrazioneBase {

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

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions putCustomer(JwtRequestPostProcessor jwt, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/customers/" + id)
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions putCustomer(UUID tenant, String role, UUID id, String json) throws Exception {
        return putCustomer(JwtDiTest.user(tenant, role), id, json);
    }

    private ResultActions deleteCustomer(UUID tenant, String role, UUID id) throws Exception {
        return mvc.perform(delete("/api/customers/" + id).with(JwtDiTest.user(tenant, role)));
    }

    /** Legge la riga anche se cancellata logicamente, nel tenant indicato. */
    private Cliente row(UUID tenant, UUID id) {
        return ContestoTenant.callAs(tenant, () -> customers.findByIdAndTenantId(id, tenant)).orElseThrow();
    }

    @Test
    void operatorCreatesAndUpdatesIdempotently() throws Exception {
        UUID id = UUID.randomUUID();

        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi S.r.l.\",\"email\":\"info@rossi.example\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato.
        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi S.r.l.\",\"email\":\"info@rossi.example\"}")
                .andExpect(status().isOk());
        // Il blocco ottimistico conta le modifiche effettive.
        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi S.p.A.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rossi S.p.A."))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(get("/api/customers").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Rossi S.p.A."))
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    void recordsWhoWrote() throws Exception {
        UUID id = UUID.randomUUID();
        putCustomer(JwtDiTest.person(tenantA, "sub-anna", "Anna", "anna@example.com", "operator"), id,
                "{\"name\":\"Rossi\"}").andExpect(status().isOk());
        putCustomer(JwtDiTest.person(tenantA, "sub-bruno", "Bruno", "bruno@example.com", "operator"), id,
                "{\"name\":\"Rossi S.r.l.\"}").andExpect(status().isOk());

        Cliente customer = row(tenantA, id);
        assertThat(customer.getCreatedBy()).isEqualTo("sub-anna");
        assertThat(customer.getUpdatedBy()).isEqualTo("sub-bruno");
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        putCustomer(tenantA, "operator", id, "{\"name\":\"Da cancellare\"}").andExpect(status().isOk());

        deleteCustomer(tenantA, "operator", id).andExpect(status().isNoContent());

        mvc.perform(get("/api/customers").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(0));
        // La riga resta come tombstone: servirà a propagare la cancellazione ai dispositivi offline.
        Cliente deleted = row(tenantA, id);
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getUpdatedBy()).isEqualTo("user-operator");

        // Reinvio dalla outbox: nessun errore e nessun cambiamento (l'ora della cancellazione resta).
        deleteCustomer(tenantA, "operator", id).andExpect(status().isNoContent());
        Cliente again = row(tenantA, id);
        assertThat(again.getDeletedAt()).isEqualTo(deleted.getDeletedAt());
        assertThat(again.getVersion()).isEqualTo(deleted.getVersion());

        // Id mai esistito: la outbox deve poter svuotarsi comunque.
        deleteCustomer(tenantA, "operator", UUID.randomUUID()).andExpect(status().isNoContent());
    }

    @Test
    void putAfterDeleteBringsTheCustomerBack() throws Exception {
        UUID id = UUID.randomUUID();
        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi\"}").andExpect(status().isOk());
        deleteCustomer(tenantA, "operator", id).andExpect(status().isNoContent());

        putCustomer(tenantA, "operator", id, "{\"name\":\"Rossi riattivato\"}").andExpect(status().isOk());

        assertThat(row(tenantA, id).isDeleted()).isFalse();
        mvc.perform(get("/api/customers").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Rossi riattivato"));
    }

    @Test
    void viewerCannotWrite() throws Exception {
        putCustomer(tenantA, "viewer", UUID.randomUUID(), "{\"name\":\"Bianchi\"}")
                .andExpect(status().isForbidden());
        deleteCustomer(tenantA, "viewer", UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    void userWithoutRolesCannotRead() throws Exception {
        mvc.perform(get("/api/customers").with(JwtDiTest.user(tenantA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customersOfAnotherTenantAreNeitherVisibleNorOverwritable() throws Exception {
        UUID id = UUID.randomUUID();
        putCustomer(tenantA, "operator", id, "{\"name\":\"Cliente di A\"}").andExpect(status().isOk());

        mvc.perform(get("/api/customers").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        // Stesso id da un altro tenant: la riga non è visibile e l'inserimento va in conflitto.
        putCustomer(tenantB, "admin", id, "{\"name\":\"Furto\"}").andExpect(status().isConflict());

        mvc.perform(get("/api/customers").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content[0].name").value("Cliente di A"));
    }

    @Test
    void customersOfAnotherTenantCannotBeDeleted() throws Exception {
        UUID id = UUID.randomUUID();
        putCustomer(tenantA, "operator", id, "{\"name\":\"Cliente di A\"}").andExpect(status().isOk());

        // Risposta indistinguibile da quella per un id inesistente: non rivela nulla.
        deleteCustomer(tenantB, "admin", id).andExpect(status().isNoContent());

        assertThat(row(tenantA, id).isDeleted()).isFalse();
        mvc.perform(get("/api/customers").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\" \"}").andExpect(status().isBadRequest());
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"X\",\"email\":\"non-una-email\"}")
                .andExpect(status().isBadRequest());
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"X\",\"partitaIva\":\"123\"}")
                .andExpect(status().isBadRequest());
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"X\",\"codiceSdi\":\"ABC\"}")
                .andExpect(status().isBadRequest());
    }

    /** Voce 2.1: anagrafica clienti completa (P.IVA, codice fiscale, indirizzo, PEC, codice SDI, condizioni). */
    @Test
    void storesFiscalAndCommercialData() throws Exception {
        UUID id = UUID.randomUUID();
        putCustomer(tenantA, "operator", id, """
                {"name":"Rossi S.r.l.","email":"info@rossi.example","partitaIva":"12345678901",
                 "codiceFiscale":"12345678901","indirizzo":"Via Roma 1, Milano","pec":"rossi@pec.example",
                 "codiceSdi":"ABC1234","condizioniPagamento":"30 giorni data fattura"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitaIva").value("12345678901"))
                .andExpect(jsonPath("$.codiceFiscale").value("12345678901"))
                .andExpect(jsonPath("$.indirizzo").value("Via Roma 1, Milano"))
                .andExpect(jsonPath("$.pec").value("rossi@pec.example"))
                .andExpect(jsonPath("$.codiceSdi").value("ABC1234"))
                .andExpect(jsonPath("$.condizioniPagamento").value("30 giorni data fattura"));

        // Dati fiscali opzionali: un cliente privato può non averne nessuno.
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"Privato\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitaIva").doesNotExist());
    }

    /** Contratto delle liste paginate (ADR 0014): page/size/sort/q e la forma esplicita di PaginaDto. */
    @Test
    void listIsPagedSortedAndSearchable() throws Exception {
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"Bianchi\",\"email\":\"a@example.com\"}")
                .andExpect(status().isOk());
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"Rossi\",\"email\":\"b@example.com\"}")
                .andExpect(status().isOk());
        putCustomer(tenantA, "operator", UUID.randomUUID(), "{\"name\":\"Verdi\",\"email\":\"c@example.com\"}")
                .andExpect(status().isOk());

        // Pagina piccola: solo i metadati e il contenuto richiesti, ordinamento predefinito per nome.
        mvc.perform(get("/api/customers?page=0&size=2").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Bianchi"))
                .andExpect(jsonPath("$.content[1].name").value("Rossi"));

        // sort= esplicito, formato Spring.
        mvc.perform(get("/api/customers?sort=name,desc").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content[0].name").value("Verdi"));

        // q= ricerca libera, su nome o email, senza distinzione di maiuscole.
        mvc.perform(get("/api/customers?q=ROSSI").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Rossi"));
        mvc.perform(get("/api/customers?q=b@example").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Rossi"));
        mvc.perform(get("/api/customers?q=nessuna-corrispondenza").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
