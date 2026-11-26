package it.gestionale.api.auth;

import java.util.Map;

/**
 * Permesso di lettura e di scrittura di ogni entità di dominio a cui si possono appendere
 * allegati e che compare in {@code GET /api/sync/changes}: un'unica mappa, non una per modulo,
 * così {@code SyncService} e il controllo per-entità di {@code AttachmentService} non possono
 * finire a dire cose diverse sullo stesso {@code entityType}. Un {@code entityType} assente non
 * è raggiungibile da nessuno (difesa in profondità contro un valore non previsto).
 */
public final class DomainEntityPermissions {

    public record EntityPermissions(Permission read, Permission write) {
    }

    private static final Map<String, EntityPermissions> BY_ENTITY_TYPE = Map.ofEntries(
            Map.entry("customers", new EntityPermissions(Permission.CUSTOMERS_READ, Permission.CUSTOMERS_WRITE)),
            Map.entry("suppliers", new EntityPermissions(Permission.SUPPLIERS_READ, Permission.SUPPLIERS_WRITE)),
            Map.entry("resources", new EntityPermissions(Permission.RESOURCES_READ, Permission.RESOURCES_WRITE)),
            Map.entry("commesse", new EntityPermissions(Permission.COMMESSE_READ, Permission.COMMESSE_WRITE)),
            Map.entry("resource_assignments", new EntityPermissions(Permission.RESOURCE_ASSIGNMENTS_READ,
                    Permission.RESOURCE_ASSIGNMENTS_WRITE)),
            Map.entry("work_activities", new EntityPermissions(Permission.WORK_ACTIVITIES_READ,
                    Permission.WORK_ACTIVITIES_WRITE)),
            Map.entry("daily_reports", new EntityPermissions(Permission.DAILY_REPORTS_READ,
                    Permission.DAILY_REPORTS_WRITE)),
            Map.entry("work_entries", new EntityPermissions(Permission.WORK_ENTRIES_READ,
                    Permission.WORK_ENTRIES_WRITE)),
            Map.entry("daily_report_materials", new EntityPermissions(Permission.DAILY_REPORT_MATERIALS_READ,
                    Permission.DAILY_REPORT_MATERIALS_WRITE)));

    private DomainEntityPermissions() {
    }

    /** Null se {@code entityType} non è un'entità di dominio conosciuta. */
    public static EntityPermissions of(String entityType) {
        return BY_ENTITY_TYPE.get(entityType);
    }
}
