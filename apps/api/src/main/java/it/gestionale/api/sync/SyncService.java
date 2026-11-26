package it.gestionale.api.sync;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gestionale.api.auth.Permission;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaDto;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerDto;
import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.dailyreportmaterials.DailyReportMaterial;
import it.gestionale.api.dailyreportmaterials.DailyReportMaterialDto;
import it.gestionale.api.dailyreportmaterials.DailyReportMaterialRepository;
import it.gestionale.api.dailyreports.DailyReport;
import it.gestionale.api.dailyreports.DailyReportDto;
import it.gestionale.api.dailyreports.DailyReportRepository;
import it.gestionale.api.resourceassignments.ResourceAssignment;
import it.gestionale.api.resourceassignments.ResourceAssignmentDto;
import it.gestionale.api.resourceassignments.ResourceAssignmentRepository;
import it.gestionale.api.resources.Resource;
import it.gestionale.api.resources.ResourceDto;
import it.gestionale.api.resources.ResourceRepository;
import it.gestionale.api.suppliers.Supplier;
import it.gestionale.api.suppliers.SupplierDto;
import it.gestionale.api.suppliers.SupplierRepository;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.workactivities.WorkActivity;
import it.gestionale.api.workactivities.WorkActivityDto;
import it.gestionale.api.workactivities.WorkActivityRepository;
import it.gestionale.api.workentries.WorkEntry;
import it.gestionale.api.workentries.WorkEntryDto;
import it.gestionale.api.workentries.WorkEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/sync/changes}: sincronizzazione incrementale lato server (voce 1.2 della
 * roadmap). Il client (voce 1.3, non qui) chiamerà questo endpoint con il cursore ricevuto
 * l'ultima volta e applicherà localmente le righe restituite, comprese le cancellazioni.
 *
 * <p>Entità incluse: le tabelle di dominio con cancellazione logica e {@code updated_at}
 * (ADR 0018) — {@code customers}, {@code suppliers}, {@code resources}, {@code commesse},
 * {@code resource_assignments}, {@code work_activities}, {@code daily_reports}, {@code work_entries},
 * {@code daily_report_materials}. {@code notifications} resta fuori: non ha quelle colonne (è
 * un registro di soli inserimenti, con stato di lettura a parte) e ha già un proprio canale
 * (campanella + outbox, vedi {@code docs/architettura.md}).
 *
 * <p>Ogni entità compare nella risposta solo se l'utente ha il permesso di lettura corrispondente:
 * l'endpoint ne copre più di una, quindi il controllo non può stare in un unico
 * {@code @PreAuthorize} e si fa qui, entità per entità (difesa in profondità, la stessa idea di
 * {@code TenantContext.require()} nei servizi che filtrano già per RLS).
 */
@Service
public class SyncService {

    static final int DEFAULT_LIMIT = 500;
    static final int MAX_LIMIT = 2000;

    private final ObjectMapper objectMapper;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final ResourceRepository resources;
    private final CommessaRepository commesse;
    private final ResourceAssignmentRepository resourceAssignments;
    private final WorkActivityRepository workActivities;
    private final DailyReportRepository dailyReports;
    private final WorkEntryRepository workEntries;
    private final DailyReportMaterialRepository dailyReportMaterials;

    public SyncService(ObjectMapper objectMapper, CustomerRepository customers, SupplierRepository suppliers,
                       ResourceRepository resources, CommessaRepository commesse,
                       ResourceAssignmentRepository resourceAssignments, WorkActivityRepository workActivities,
                       DailyReportRepository dailyReports, WorkEntryRepository workEntries,
                       DailyReportMaterialRepository dailyReportMaterials) {
        this.objectMapper = objectMapper;
        this.customers = customers;
        this.suppliers = suppliers;
        this.resources = resources;
        this.commesse = commesse;
        this.resourceAssignments = resourceAssignments;
        this.workActivities = workActivities;
        this.dailyReports = dailyReports;
        this.workEntries = workEntries;
        this.dailyReportMaterials = dailyReportMaterials;
    }

    @Transactional(readOnly = true)
    public SyncChangesDto changes(String rawCursor, Integer rawLimit, Set<String> authorities) {
        UUID tenantId = TenantContext.require();
        Map<String, SyncCursorEntry> cursorIn = decodeCursor(rawCursor);
        int limit = rawLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(rawLimit, MAX_LIMIT));
        boolean laborCostRead = authorities.contains(Permission.LABOR_COST_READ.code());

        Map<String, SyncCursorEntry> cursorOut = new LinkedHashMap<>();
        Map<String, List<SyncChangeDto>> entities = new LinkedHashMap<>();
        boolean hasMore = false;

        hasMore |= syncEntity("customers", Permission.CUSTOMERS_READ, authorities, cursorIn, cursorOut, entities,
                limit, (after, pageable) -> customers.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Customer::getId, Customer::getUpdatedAt, Customer::getVersion, Customer::isDeleted, CustomerDto::from);

        hasMore |= syncEntity("suppliers", Permission.SUPPLIERS_READ, authorities, cursorIn, cursorOut, entities,
                limit, (after, pageable) -> suppliers.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Supplier::getId, Supplier::getUpdatedAt, Supplier::getVersion, Supplier::isDeleted, SupplierDto::from);

        hasMore |= syncEntity("resources", Permission.RESOURCES_READ, authorities, cursorIn, cursorOut, entities,
                limit, (after, pageable) -> resources.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Resource::getId, Resource::getUpdatedAt, Resource::getVersion, Resource::isDeleted, ResourceDto::from);

        hasMore |= syncEntity("commesse", Permission.COMMESSE_READ, authorities, cursorIn, cursorOut, entities, limit,
                (after, pageable) -> commesse.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Commessa::getId, Commessa::getUpdatedAt, Commessa::getVersion, Commessa::isDeleted, CommessaDto::from);

        hasMore |= syncEntity("resource_assignments", Permission.RESOURCE_ASSIGNMENTS_READ, authorities, cursorIn,
                cursorOut, entities, limit,
                (after, pageable) -> resourceAssignments.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                ResourceAssignment::getId, ResourceAssignment::getUpdatedAt, ResourceAssignment::getVersion,
                ResourceAssignment::isDeleted,
                withoutHourlyCostOverrideUnless(laborCostRead));

        hasMore |= syncEntity("work_activities", Permission.WORK_ACTIVITIES_READ, authorities, cursorIn, cursorOut,
                entities, limit,
                (after, pageable) -> workActivities.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                WorkActivity::getId, WorkActivity::getUpdatedAt, WorkActivity::getVersion, WorkActivity::isDeleted,
                WorkActivityDto::from);

        hasMore |= syncEntity("daily_reports", Permission.DAILY_REPORTS_READ, authorities, cursorIn, cursorOut,
                entities, limit,
                (after, pageable) -> dailyReports.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                DailyReport::getId, DailyReport::getUpdatedAt, DailyReport::getVersion, DailyReport::isDeleted,
                DailyReportDto::from);

        hasMore |= syncEntity("work_entries", Permission.WORK_ENTRIES_READ, authorities, cursorIn, cursorOut,
                entities, limit,
                (after, pageable) -> workEntries.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                WorkEntry::getId, WorkEntry::getUpdatedAt, WorkEntry::getVersion, WorkEntry::isDeleted,
                WorkEntryDto::from);

        hasMore |= syncEntity("daily_report_materials", Permission.DAILY_REPORT_MATERIALS_READ, authorities, cursorIn,
                cursorOut, entities, limit,
                (after, pageable) -> dailyReportMaterials.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                DailyReportMaterial::getId, DailyReportMaterial::getUpdatedAt, DailyReportMaterial::getVersion,
                DailyReportMaterial::isDeleted, DailyReportMaterialDto::from);

        return new SyncChangesDto(encodeCursor(cursorOut), hasMore, entities);
    }

    /**
     * {@code hourlyCostOverride} va nascosto a chi non ha {@code labor-cost:read}, come già fa
     * {@code GET /api/commesse/{id}/resource-assignments} (ADR 0021): stessa regola qui.
     */
    private static Function<ResourceAssignment, Object> withoutHourlyCostOverrideUnless(boolean laborCostRead) {
        return a -> {
            ResourceAssignmentDto dto = ResourceAssignmentDto.from(a);
            return laborCostRead ? dto : dto.withoutHourlyCostOverride();
        };
    }

    /**
     * Interroga, pagina e mappa un'entità, se l'utente ha il permesso di lettura corrispondente.
     * Richiede {@code limit + 1} righe per sapere se ce ne sono altre senza una {@code COUNT}
     * separata. Se non ci sono risultati il cursore dell'entità resta quello in ingresso: non
     * c'è nulla da far avanzare, e la prossima chiamata ripartirà dallo stesso punto.
     *
     * @return vero se questa entità ha raggiunto il limite di pagina (altre modifiche in coda)
     */
    private <T> boolean syncEntity(String entityName, Permission readPermission, Set<String> authorities,
                                   Map<String, SyncCursorEntry> cursorIn, Map<String, SyncCursorEntry> cursorOut,
                                   Map<String, List<SyncChangeDto>> out, int limit, ChangesQuery<T> query,
                                   Function<T, UUID> idOf, Function<T, Instant> updatedAtOf,
                                   Function<T, Long> versionOf, Function<T, Boolean> deletedOf,
                                   Function<T, Object> dataOf) {
        if (!authorities.contains(readPermission.code())) {
            return false;
        }
        SyncCursorEntry after = cursorIn.getOrDefault(entityName, SyncCursorEntry.BEGINNING);
        Pageable page = PageRequest.of(0, limit + 1);
        List<T> rows = query.fetch(after, page);

        boolean hasMore = rows.size() > limit;
        List<T> changed = hasMore ? rows.subList(0, limit) : rows;
        out.put(entityName, changed.stream()
                .map(row -> new SyncChangeDto(idOf.apply(row), updatedAtOf.apply(row), versionOf.apply(row),
                        deletedOf.apply(row), deletedOf.apply(row) ? null : dataOf.apply(row)))
                .toList());
        cursorOut.put(entityName, changed.isEmpty() ? after
                : new SyncCursorEntry(updatedAtOf.apply(changed.get(changed.size() - 1)),
                        idOf.apply(changed.get(changed.size() - 1))));
        return hasMore;
    }

    private Map<String, SyncCursorEntry> decodeCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(raw);
            return objectMapper.readValue(json, new TypeReference<Map<String, SyncCursorEntry>>() {
            });
        } catch (IllegalArgumentException | IOException e) {
            throw new InvalidSyncCursorException("Cursore di sincronizzazione non valido", e);
        }
    }

    private String encodeCursor(Map<String, SyncCursorEntry> cursor) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(cursor));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile codificare il cursore di sincronizzazione", e);
        }
    }

    @FunctionalInterface
    private interface ChangesQuery<T> {
        List<T> fetch(SyncCursorEntry after, Pageable pageable);
    }
}
