package it.gestionale.api.accesso;

import java.util.Map;

/**
 * Permesso di lettura e di scrittura di ogni entità di dominio a cui si possono appendere
 * allegati e che compare in {@code GET /api/sync/changes}: un'unica mappa, non una per modulo,
 * così {@code SincronizzazioneService} e il controllo per-entità di {@code AllegatoService} non possono
 * finire a dire cose diverse sullo stesso {@code entityType}. Un {@code entityType} assente non
 * è raggiungibile da nessuno (difesa in profondità contro un valore non previsto).
 */
public final class PermessiEntitaDominio {

    public record PermessiEntita(Permesso read, Permesso write) {
    }

    private static final Map<String, PermessiEntita> BY_ENTITY_TYPE = Map.ofEntries(
            Map.entry("customers", new PermessiEntita(Permesso.CUSTOMERS_READ, Permesso.CUSTOMERS_WRITE)),
            Map.entry("suppliers", new PermessiEntita(Permesso.SUPPLIERS_READ, Permesso.SUPPLIERS_WRITE)),
            Map.entry("resources", new PermessiEntita(Permesso.RESOURCES_READ, Permesso.RESOURCES_WRITE)),
            Map.entry("commesse", new PermessiEntita(Permesso.COMMESSE_READ, Permesso.COMMESSE_WRITE)),
            Map.entry("resource_assignments", new PermessiEntita(Permesso.RESOURCE_ASSIGNMENTS_READ,
                    Permesso.RESOURCE_ASSIGNMENTS_WRITE)),
            Map.entry("work_activities", new PermessiEntita(Permesso.WORK_ACTIVITIES_READ,
                    Permesso.WORK_ACTIVITIES_WRITE)),
            Map.entry("daily_reports", new PermessiEntita(Permesso.DAILY_REPORTS_READ,
                    Permesso.DAILY_REPORTS_WRITE)),
            Map.entry("work_entries", new PermessiEntita(Permesso.WORK_ENTRIES_READ,
                    Permesso.WORK_ENTRIES_WRITE)),
            Map.entry("daily_report_materials", new PermessiEntita(Permesso.DAILY_REPORT_MATERIALS_READ,
                    Permesso.DAILY_REPORT_MATERIALS_WRITE)));

    private PermessiEntitaDominio() {
    }

    /** Null se {@code entityType} non è un'entità di dominio conosciuta. */
    public static PermessiEntita of(String entityType) {
        return BY_ENTITY_TYPE.get(entityType);
    }
}
