package it.gestionale.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.allegati.Allegato;
import it.gestionale.api.allegati.AllegatoRepository;
import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.commesse.StatoCommessa;
import it.gestionale.api.fornitori.Fornitore;
import it.gestionale.api.fornitori.FornitoreRepository;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.risorse.Risorsa;
import it.gestionale.api.risorse.RisorsaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifica che la Row Level Security isoli i dati di due tenant. */
@AutoConfigureMockMvc
class IsolamentoTenantTest extends TestIntegrazioneBase {

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
    RisorsaRepository resources;
    @Autowired
    FornitoreRepository suppliers;
    @Autowired
    AllegatoRepository attachments;
    @Autowired
    CommessaRepository commesse;
    @Autowired
    JdbcTemplate jdbc;

    UUID tenantA;
    UUID tenantB;
    UUID customerA;
    UUID customerB;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "pro", Set.of("aiAssistant"), 100);
        customerA = UUID.randomUUID();
        customerB = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> customers.save(new Cliente(customerA, tenantA, "Cliente A", null, "u-a")));
        ContestoTenant.runAs(tenantB, () -> customers.save(new Cliente(customerB, tenantB, "Cliente B", null, "u-b")));
    }

    @Test
    void applicationRoleIsNotSuperuserAndCannotBypassRls() {
        Boolean bypass = jdbc.queryForObject(
                "select rolsuper or rolbypassrls from pg_roles where rolname = current_user", Boolean.class);
        assertThat(bypass).isFalse();
    }

    @Test
    void eachTenantSeesOnlyItsOwnRows() {
        List<Cliente> seenByA = ContestoTenant.callAs(tenantA, customers::findAll);
        List<Cliente> seenByB = ContestoTenant.callAs(tenantB, customers::findAll);

        assertThat(seenByA).extracting(Cliente::getTenantId).containsOnly(tenantA);
        assertThat(seenByA).extracting(Cliente::getId).contains(customerA).doesNotContain(customerB);
        assertThat(seenByB).extracting(Cliente::getId).contains(customerB).doesNotContain(customerA);
        assertThat(ContestoTenant.callAs(tenantA, () -> customers.findById(customerB))).isEmpty();
        assertThat(ContestoTenant.callAs(tenantA, () -> tenants.findAll()))
                .extracting(Tenant::getId).containsExactly(tenantA);
    }

    @Test
    void withoutTenantNothingIsVisible() {
        assertThat(ContestoTenant.current()).isEmpty();
        assertThat(customers.findAll()).isEmpty();
        assertThat(tenants.findAll()).isEmpty();
    }

    @Test
    void cannotWriteRowsOfAnotherTenant() {
        assertThatThrownBy(() -> ContestoTenant.runAs(tenantA,
                () -> customers.save(new Cliente(UUID.randomUUID(), tenantB, "Intruso", null, "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    /**
     * Stesso controllo di {@link #eachTenantSeesOnlyItsOwnRows} e {@link #cannotWriteRowsOfAnotherTenant},
     * ma su {@code resources}: non basta che il meccanismo RLS funzioni (lo prova già Cliente),
     * serve verificare che sia stato davvero attivato su questa tabella (una nuova entità che
     * dimentica {@code enable_tenant_rls} in una migrazione non darebbe altrimenti nessun errore).
     */
    @Test
    void resourcesAreIsolatedByTenant() {
        UUID resourceA = UUID.randomUUID();
        UUID resourceB = UUID.randomUUID();
        ContestoTenant.runAs(tenantA,
                () -> resources.save(new Risorsa(resourceA, tenantA, "Mario", "Rossi", null, null, "u-a")));
        ContestoTenant.runAs(tenantB,
                () -> resources.save(new Risorsa(resourceB, tenantB, "Luigi", "Bianchi", null, null, "u-b")));

        List<Risorsa> seenByA = ContestoTenant.callAs(tenantA, resources::findAll);
        assertThat(seenByA).extracting(Risorsa::getId).containsExactly(resourceA);
        assertThatThrownBy(() -> ContestoTenant.runAs(tenantA,
                () -> resources.save(new Risorsa(UUID.randomUUID(), tenantB, "Intruso", "Intruso", null, null, "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    /** Come {@link #resourcesAreIsolatedByTenant}, ma su {@code suppliers} (roadmap 2.2, tabella propria). */
    @Test
    void suppliersAreIsolatedByTenant() {
        UUID supplierA = UUID.randomUUID();
        UUID supplierB = UUID.randomUUID();
        ContestoTenant.runAs(tenantA,
                () -> suppliers.save(new Fornitore(supplierA, tenantA, "Fornitore A", null, null, null, null, null, null, null, "u-a")));
        ContestoTenant.runAs(tenantB,
                () -> suppliers.save(new Fornitore(supplierB, tenantB, "Fornitore B", null, null, null, null, null, null, null, "u-b")));

        List<Fornitore> seenByA = ContestoTenant.callAs(tenantA, suppliers::findAll);
        assertThat(seenByA).extracting(Fornitore::getId).containsExactly(supplierA);
        assertThatThrownBy(() -> ContestoTenant.runAs(tenantA, () -> suppliers.save(
                new Fornitore(UUID.randomUUID(), tenantB, "Intruso", null, null, null, null, null, null, null, "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    /** Come {@link #resourcesAreIsolatedByTenant}, ma su {@code attachments} (roadmap 2.4). */
    @Test
    void attachmentsAreIsolatedByTenant() {
        UUID attachmentA = UUID.randomUUID();
        UUID attachmentB = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> attachments.save(new Allegato(attachmentA, tenantA, "daily_reports",
                UUID.randomUUID(), "foto.jpg", "image/jpeg", 1024, "a".repeat(64), "key-a", "u-a")));
        ContestoTenant.runAs(tenantB, () -> attachments.save(new Allegato(attachmentB, tenantB, "daily_reports",
                UUID.randomUUID(), "foto.jpg", "image/jpeg", 1024, "b".repeat(64), "key-b", "u-b")));

        List<Allegato> seenByA = ContestoTenant.callAs(tenantA, attachments::findAll);
        assertThat(seenByA).extracting(Allegato::getId).containsExactly(attachmentA);
        assertThatThrownBy(() -> ContestoTenant.runAs(tenantA, () -> attachments.save(new Allegato(
                UUID.randomUUID(), tenantB, "daily_reports", UUID.randomUUID(), "intruso.jpg", "image/jpeg", 1,
                "c".repeat(64), "key-c", "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    /** Come {@link #resourcesAreIsolatedByTenant}, ma su {@code commesse}. */
    @Test
    void commesseAreIsolatedByTenant() {
        UUID commessaA = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> commesse.save(
                new Commessa(commessaA, tenantA, customerA, "Tetto", null, StatoCommessa.PIANIFICATA, "u-a")));

        List<Commessa> seenByA = ContestoTenant.callAs(tenantA, commesse::findAll);
        assertThat(seenByA).extracting(Commessa::getId).containsExactly(commessaA);
        assertThatThrownBy(() -> ContestoTenant.runAs(tenantA, () -> commesse.save(new Commessa(UUID.randomUUID(),
                tenantB, customerB, "Furto", null, StatoCommessa.PIANIFICATA, "u-a"))))
                .hasStackTraceContaining("row-level security");
    }

    @Test
    void entitlementsEndpointReturnsDataOfTheTokenTenant() throws Exception {
        mvc.perform(get("/api/entitlements").with(jwt().jwt(j -> j.claim("tenant_id", tenantA.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("base"));
        mvc.perform(get("/api/entitlements").with(jwt().jwt(j -> j.claim("tenant_id", tenantB.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("pro"))
                .andExpect(jsonPath("$.features[0]").value("aiAssistant"));
    }

    @Test
    void requestsWithoutValidTokenOrTenantAreRejected() throws Exception {
        mvc.perform(get("/api/entitlements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/entitlements").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/api/entitlements").with(jwt().jwt(j -> j.claim("tenant_id", "non-un-uuid"))))
                .andExpect(status().isForbidden());
    }
}
