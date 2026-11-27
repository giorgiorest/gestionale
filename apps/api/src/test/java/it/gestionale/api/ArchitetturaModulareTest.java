package it.gestionale.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Fa rispettare il monolite modulare (ADR 0023). Ogni sotto-package diretto di
 * {@code it.gestionale.api} è un modulo; {@code piattaforma.identita} appartiene a {@code piattaforma}.
 *
 * <p>Le dipendenze vanno solo dall'alto verso il basso:
 * <pre>
 *   orchestrazione   sincronizzazione, piattaforma, demo, errori, configurazione, salute
 *        ↓
 *   dominio          clienti, fornitori, risorse, commesse, assegnazioni, lavorazioni,
 *                    rapportini, riepilogoore, costimanodopera
 *        ↓
 *   trasversali      notifiche, allegati, ia, osservabilita
 *        ↓
 *   sicurezza        accesso, licenze
 *        ↓
 *   fondamenta       tenant, condiviso
 * </pre>
 * Un modulo che ha bisogno di reagire a un altro dello stesso livello o di uno superiore non lo
 * chiama: pubblica un evento (vedi {@code UtenteRegistrato}).
 *
 * <p>Test puro (nessun contesto Spring, nessun database): gira in pochi secondi.
 */
@AnalyzeClasses(packages = "it.gestionale.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitetturaModulareTest {

    private static final String ORCHESTRAZIONE = "Orchestrazione";
    private static final String DOMINIO = "Dominio";
    private static final String TRASVERSALI = "Trasversali";
    private static final String SICUREZZA = "Sicurezza";
    private static final String FONDAMENTA = "Fondamenta";

    @ArchTest
    static final ArchRule nessun_ciclo_tra_i_moduli = slices()
            .matching("it.gestionale.api.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule le_dipendenze_vanno_solo_verso_il_basso = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer(ORCHESTRAZIONE).definedBy(
                    "it.gestionale.api.sincronizzazione..", "it.gestionale.api.piattaforma..",
                    "it.gestionale.api.demo..", "it.gestionale.api.errori..",
                    "it.gestionale.api.configurazione..", "it.gestionale.api.salute..")
            .layer(DOMINIO).definedBy(
                    "it.gestionale.api.clienti..", "it.gestionale.api.fornitori..",
                    "it.gestionale.api.risorse..", "it.gestionale.api.commesse..",
                    "it.gestionale.api.assegnazioni..", "it.gestionale.api.lavorazioni..",
                    "it.gestionale.api.rapportini..", "it.gestionale.api.riepilogoore..",
                    "it.gestionale.api.costimanodopera..")
            .layer(TRASVERSALI).definedBy(
                    "it.gestionale.api.notifiche..", "it.gestionale.api.allegati..",
                    "it.gestionale.api.ia..", "it.gestionale.api.osservabilita..")
            .layer(SICUREZZA).definedBy("it.gestionale.api.accesso..", "it.gestionale.api.licenze..")
            .layer(FONDAMENTA).definedBy("it.gestionale.api.tenant..", "it.gestionale.api.condiviso..")
            .whereLayer(ORCHESTRAZIONE).mayNotBeAccessedByAnyLayer()
            .whereLayer(DOMINIO).mayOnlyBeAccessedByLayers(ORCHESTRAZIONE)
            .whereLayer(TRASVERSALI).mayOnlyBeAccessedByLayers(DOMINIO, ORCHESTRAZIONE)
            .whereLayer(SICUREZZA).mayOnlyBeAccessedByLayers(TRASVERSALI, DOMINIO, ORCHESTRAZIONE);
}
