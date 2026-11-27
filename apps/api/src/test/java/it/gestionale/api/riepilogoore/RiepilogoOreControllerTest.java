package it.gestionale.api.riepilogoore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import it.gestionale.api.lavorazioni.Lavorazione;
import it.gestionale.api.lavorazioni.LavorazioneRepository;
import it.gestionale.api.lavorazioni.StatoLavorazione;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.rapportini.Rapportino;
import it.gestionale.api.rapportini.RapportinoRepository;
import it.gestionale.api.rapportini.RigaOre;
import it.gestionale.api.rapportini.RigaOreRepository;
import it.gestionale.api.risorse.Risorsa;
import it.gestionale.api.risorse.RisorsaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Le ore sono distribuite così, nel gennaio 2026 (settimana ISO da lunedì):
 * <ul>
 * <li>5/1 (lunedì, settimana del 5/1): resourceA, activityX, 8h</li>
 * <li>6/1 (martedì, stessa settimana): resourceA, activityX, 4h + 1h straordinario</li>
 * <li>12/1 (lunedì, settimana del 12/1): resourceB, activityY, 6h</li>
 * </ul>
 * più una riga cancellata e una riga di febbraio (fuori dall'intervallo interrogato), per
 * verificare che entrambe non contino.
 */
@AutoConfigureMockMvc
class RiepilogoOreControllerTest extends TestIntegrazioneBase {

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
    RisorsaRepository resources;
    @Autowired
    LavorazioneRepository activities;
    @Autowired
    RapportinoRepository reports;
    @Autowired
    RigaOreRepository entries;

    UUID tenantA;
    UUID commessaA;
    UUID resourceA;
    UUID resourceB;
    UUID activityX;
    UUID activityY;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        commessaA = UUID.randomUUID();
        resourceA = UUID.randomUUID();
        resourceB = UUID.randomUUID();
        activityX = UUID.randomUUID();
        activityY = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        UUID reportJan5 = UUID.randomUUID();
        UUID reportJan6 = UUID.randomUUID();
        UUID reportJan12 = UUID.randomUUID();
        UUID reportFeb2 = UUID.randomUUID();
        UUID deletedReport = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerA, tenantA, "Cliente A", null, null));
            commesse.save(new Commessa(commessaA, tenantA, customerA, "Tetto", null, StatoCommessa.PIANIFICATA,
                    "u-a"));
            resources.save(new Risorsa(resourceA, tenantA, "Mario", "Rossi", null, null, "u-a"));
            resources.save(new Risorsa(resourceB, tenantA, "Luigi", "Bianchi", null, null, "u-a"));
            activities.save(new Lavorazione(activityX, tenantA, commessaA, null, "ARM-01", "Armatura", null,
                    StatoLavorazione.PIANIFICATA, 0, "u-a"));
            activities.save(new Lavorazione(activityY, tenantA, commessaA, null, "MUR-01", "Muratura", null,
                    StatoLavorazione.PIANIFICATA, 0, "u-a"));
            reports.save(new Rapportino(reportJan5, tenantA, commessaA, LocalDate.parse("2026-01-05"), null, null,
                    "u-a"));
            reports.save(new Rapportino(reportJan6, tenantA, commessaA, LocalDate.parse("2026-01-06"), null, null,
                    "u-a"));
            reports.save(new Rapportino(reportJan12, tenantA, commessaA, LocalDate.parse("2026-01-12"), null, null,
                    "u-a"));
            reports.save(new Rapportino(reportFeb2, tenantA, commessaA, LocalDate.parse("2026-02-02"), null, null,
                    "u-a"));
            entries.save(new RigaOre(UUID.randomUUID(), tenantA, reportJan5, resourceA, activityX,
                    new BigDecimal("8"), BigDecimal.ZERO, null, "u-a"));
            entries.save(new RigaOre(UUID.randomUUID(), tenantA, reportJan6, resourceA, activityX,
                    new BigDecimal("4"), new BigDecimal("1"), null, "u-a"));
            entries.save(new RigaOre(UUID.randomUUID(), tenantA, reportJan12, resourceB, activityY,
                    new BigDecimal("6"), BigDecimal.ZERO, null, "u-a"));
            // Fuori dall'intervallo interrogato (febbraio): non deve comparire.
            entries.save(new RigaOre(UUID.randomUUID(), tenantA, reportFeb2, resourceA, activityY,
                    new BigDecimal("5"), BigDecimal.ZERO, null, "u-a"));
            // Cancellata logicamente: non deve comparire.
            RigaOre deleted = new RigaOre(UUID.randomUUID(), tenantA, reportJan5, resourceB, activityY,
                    new BigDecimal("3"), BigDecimal.ZERO, null, "u-a");
            deleted.markDeleted("u-a", java.time.Instant.now());
            entries.save(deleted);
            // Rapportino cancellato, con una riga attiva: non deve comparire.
            Rapportino cancelled = new Rapportino(deletedReport, tenantA, commessaA, LocalDate.parse("2026-01-07"),
                    null, null, "u-a");
            cancelled.markDeleted("u-a", java.time.Instant.now());
            reports.save(cancelled);
            entries.save(new RigaOre(UUID.randomUUID(), tenantA, deletedReport, resourceA, activityX,
                    new BigDecimal("2"), BigDecimal.ZERO, null, "u-a"));
        }));
    }

    private ResultActions getReport(UUID tenant, String role, UUID commessaId, String from, String to)
            throws Exception {
        return mvc.perform(get("/api/commesse/" + commessaId + "/reports/hours").param("from", from)
                .param("to", to).with(JwtDiTest.user(tenant, role)));
    }

    @Test
    void aggregatesHoursIntoTheThreeViewsWithinTheRange() throws Exception {
        getReport(tenantA, "admin", commessaA, "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly.length()").value(2))
                .andExpect(jsonPath("$.weekly[0].weekStart").value("2026-01-05"))
                .andExpect(jsonPath("$.weekly[0].hours").value(12))
                .andExpect(jsonPath("$.weekly[0].overtimeHours").value(1))
                .andExpect(jsonPath("$.weekly[1].weekStart").value("2026-01-12"))
                .andExpect(jsonPath("$.weekly[1].hours").value(6))
                .andExpect(jsonPath("$.byResource.length()").value(2))
                .andExpect(jsonPath("$.byResource[?(@.resourceId=='" + resourceA + "')].month").value("2026-01-01"))
                .andExpect(jsonPath("$.byResource[?(@.resourceId=='" + resourceA + "')].hours").value(12.0))
                .andExpect(jsonPath("$.byResource[?(@.resourceId=='" + resourceA + "')].overtimeHours").value(1.0))
                .andExpect(jsonPath("$.byResource[?(@.resourceId=='" + resourceB + "')].hours").value(6.0))
                .andExpect(jsonPath("$.byWorkActivity.length()").value(2))
                .andExpect(jsonPath("$.byWorkActivity[?(@.workActivityId=='" + activityX + "')].hours").value(12.0))
                .andExpect(jsonPath("$.byWorkActivity[?(@.workActivityId=='" + activityY + "')].hours").value(6.0));
    }

    @Test
    void entriesOutsideTheRangeDeletedOrOfAnotherCommessaAreExcluded() throws Exception {
        // Solo febbraio: l'unica riga nell'intervallo è quella (fuori range sopra) di resourceA/activityY.
        getReport(tenantA, "admin", commessaA, "2026-02-01", "2026-02-28")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly.length()").value(1))
                .andExpect(jsonPath("$.byWorkActivity.length()").value(1))
                .andExpect(jsonPath("$.byWorkActivity[0].workActivityId").value(activityY.toString()))
                .andExpect(jsonPath("$.byWorkActivity[0].hours").value(5));

        UUID customerA2 = UUID.randomUUID();
        UUID commessaA2 = UUID.randomUUID();
        ContestoTenant.runAs(tenantA, () -> tx.executeWithoutResult(s -> {
            customers.save(new Cliente(customerA2, tenantA, "Cliente A2", null, null));
            commesse.save(new Commessa(commessaA2, tenantA, customerA2, "Muro", null, StatoCommessa.PIANIFICATA,
                    "u-a"));
        }));
        // Commessa senza rapportini: report vuoto, non un errore.
        getReport(tenantA, "admin", commessaA2, "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly.length()").value(0))
                .andExpect(jsonPath("$.byResource.length()").value(0))
                .andExpect(jsonPath("$.byWorkActivity.length()").value(0));
    }

    @Test
    void rejectsAnInvertedRange() throws Exception {
        getReport(tenantA, "admin", commessaA, "2026-01-31", "2026-01-01").andExpect(status().isBadRequest());
    }

    @Test
    void operatorCapocantiereAndCaposquadraCanReadButOperaioAndAmministrativoCannot() throws Exception {
        for (String role : new String[] { "operator", "viewer", "capocantiere", "caposquadra" }) {
            getReport(tenantA, role, commessaA, "2026-01-01", "2026-01-31").andExpect(status().isOk());
        }
        for (String role : new String[] { "operaio", "amministrativo" }) {
            getReport(tenantA, role, commessaA, "2026-01-01", "2026-01-31").andExpect(status().isForbidden());
        }
    }

    @Test
    void aCommessaOfAnotherTenantIsNotVisible() throws Exception {
        UUID tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        getReport(tenantB, "admin", commessaA, "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly.length()").value(0));
    }
}
