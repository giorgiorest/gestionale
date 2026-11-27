package it.gestionale.api.sincronizzazione;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gestionale.api.accesso.PermessiEntitaDominio;
import it.gestionale.api.accesso.Permesso;
import it.gestionale.api.allegati.Allegato;
import it.gestionale.api.allegati.AllegatoDto;
import it.gestionale.api.allegati.AllegatoRepository;
import it.gestionale.api.assegnazioni.AssegnazioneRisorsa;
import it.gestionale.api.assegnazioni.AssegnazioneRisorsaDto;
import it.gestionale.api.assegnazioni.AssegnazioneRisorsaRepository;
import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteDto;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.Commessa;
import it.gestionale.api.commesse.CommessaDto;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.fornitori.Fornitore;
import it.gestionale.api.fornitori.FornitoreDto;
import it.gestionale.api.fornitori.FornitoreRepository;
import it.gestionale.api.lavorazioni.Lavorazione;
import it.gestionale.api.lavorazioni.LavorazioneDto;
import it.gestionale.api.lavorazioni.LavorazioneRepository;
import it.gestionale.api.rapportini.MaterialeRapportino;
import it.gestionale.api.rapportini.MaterialeRapportinoDto;
import it.gestionale.api.rapportini.MaterialeRapportinoRepository;
import it.gestionale.api.rapportini.Rapportino;
import it.gestionale.api.rapportini.RapportinoDto;
import it.gestionale.api.rapportini.RapportinoRepository;
import it.gestionale.api.rapportini.RigaOre;
import it.gestionale.api.rapportini.RigaOreDto;
import it.gestionale.api.rapportini.RigaOreRepository;
import it.gestionale.api.risorse.Risorsa;
import it.gestionale.api.risorse.RisorsaDto;
import it.gestionale.api.risorse.RisorsaRepository;
import it.gestionale.api.tenant.ContestoTenant;
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
 * {@code daily_report_materials}, {@code attachments}. {@code notifications} resta fuori: non ha quelle colonne (è
 * un registro di soli inserimenti, con stato di lettura a parte) e ha già un proprio canale
 * (campanella + outbox, vedi {@code docs/architettura.md}).
 *
 * <p>Ogni entità compare nella risposta solo se l'utente ha il permesso di lettura corrispondente:
 * l'endpoint ne copre più di una, quindi il controllo non può stare in un unico
 * {@code @PreAuthorize} e si fa qui, entità per entità (difesa in profondità, la stessa idea di
 * {@code ContestoTenant.require()} nei servizi che filtrano già per RLS). Per {@code attachments},
 * {@code attachments:read} da solo non basta: ogni riga si filtra anche per il permesso di
 * lettura della sua {@code entityType} (stessa mappa di {@code AllegatoService}, via
 * {@code PermessiEntitaDominio}) — altrimenti un ruolo come {@code amministrativo} vedrebbe,
 * da qui, i metadati degli allegati dei rapportini che non può leggere direttamente.
 */
@Service
public class SincronizzazioneService {

    static final int DEFAULT_LIMIT = 500;
    static final int MAX_LIMIT = 2000;

    private final ObjectMapper objectMapper;
    private final ClienteRepository customers;
    private final FornitoreRepository suppliers;
    private final RisorsaRepository resources;
    private final CommessaRepository commesse;
    private final AssegnazioneRisorsaRepository resourceAssignments;
    private final LavorazioneRepository workActivities;
    private final RapportinoRepository dailyReports;
    private final RigaOreRepository workEntries;
    private final MaterialeRapportinoRepository dailyReportMaterials;
    private final AllegatoRepository attachmentsRepo;

    public SincronizzazioneService(ObjectMapper objectMapper, ClienteRepository customers, FornitoreRepository suppliers,
                       RisorsaRepository resources, CommessaRepository commesse,
                       AssegnazioneRisorsaRepository resourceAssignments, LavorazioneRepository workActivities,
                       RapportinoRepository dailyReports, RigaOreRepository workEntries,
                       MaterialeRapportinoRepository dailyReportMaterials, AllegatoRepository attachmentsRepo) {
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
        this.attachmentsRepo = attachmentsRepo;
    }

    @Transactional(readOnly = true)
    public ModificheDto changes(String rawCursor, Integer rawLimit, Set<String> authorities) {
        UUID tenantId = ContestoTenant.require();
        Map<String, VoceCursore> cursorIn = decodeCursor(rawCursor);
        int limit = rawLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(rawLimit, MAX_LIMIT));
        boolean laborCostRead = authorities.contains(Permesso.LABOR_COST_READ.code());

        Map<String, VoceCursore> cursorOut = new LinkedHashMap<>();
        Map<String, List<ModificaDto>> entities = new LinkedHashMap<>();
        boolean hasMore = false;

        hasMore |= syncEntity("customers", Permesso.CUSTOMERS_READ, authorities, cursorIn, cursorOut, entities,
                limit, (after, pageable) -> customers.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Cliente::getId, Cliente::getUpdatedAt, Cliente::getVersion, Cliente::isDeleted, ClienteDto::from);

        hasMore |= syncEntity("suppliers", Permesso.SUPPLIERS_READ, authorities, cursorIn, cursorOut, entities,
                limit, (after, pageable) -> suppliers.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Fornitore::getId, Fornitore::getUpdatedAt, Fornitore::getVersion, Fornitore::isDeleted, FornitoreDto::from);

        hasMore |= syncEntity("resources", Permesso.RESOURCES_READ, authorities, cursorIn, cursorOut, entities,
                limit, (after, pageable) -> resources.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Risorsa::getId, Risorsa::getUpdatedAt, Risorsa::getVersion, Risorsa::isDeleted, RisorsaDto::from);

        hasMore |= syncEntity("commesse", Permesso.COMMESSE_READ, authorities, cursorIn, cursorOut, entities, limit,
                (after, pageable) -> commesse.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Commessa::getId, Commessa::getUpdatedAt, Commessa::getVersion, Commessa::isDeleted, CommessaDto::from);

        hasMore |= syncEntity("resource_assignments", Permesso.RESOURCE_ASSIGNMENTS_READ, authorities, cursorIn,
                cursorOut, entities, limit,
                (after, pageable) -> resourceAssignments.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                AssegnazioneRisorsa::getId, AssegnazioneRisorsa::getUpdatedAt, AssegnazioneRisorsa::getVersion,
                AssegnazioneRisorsa::isDeleted,
                withoutHourlyCostOverrideUnless(laborCostRead));

        hasMore |= syncEntity("work_activities", Permesso.WORK_ACTIVITIES_READ, authorities, cursorIn, cursorOut,
                entities, limit,
                (after, pageable) -> workActivities.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Lavorazione::getId, Lavorazione::getUpdatedAt, Lavorazione::getVersion, Lavorazione::isDeleted,
                LavorazioneDto::from);

        hasMore |= syncEntity("daily_reports", Permesso.DAILY_REPORTS_READ, authorities, cursorIn, cursorOut,
                entities, limit,
                (after, pageable) -> dailyReports.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Rapportino::getId, Rapportino::getUpdatedAt, Rapportino::getVersion, Rapportino::isDeleted,
                RapportinoDto::from);

        hasMore |= syncEntity("work_entries", Permesso.WORK_ENTRIES_READ, authorities, cursorIn, cursorOut,
                entities, limit,
                (after, pageable) -> workEntries.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                RigaOre::getId, RigaOre::getUpdatedAt, RigaOre::getVersion, RigaOre::isDeleted,
                RigaOreDto::from);

        hasMore |= syncEntity("daily_report_materials", Permesso.DAILY_REPORT_MATERIALS_READ, authorities, cursorIn,
                cursorOut, entities, limit,
                (after, pageable) -> dailyReportMaterials.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                MaterialeRapportino::getId, MaterialeRapportino::getUpdatedAt, MaterialeRapportino::getVersion,
                MaterialeRapportino::isDeleted, MaterialeRapportinoDto::from);

        hasMore |= syncEntity("attachments", Permesso.ATTACHMENTS_READ, authorities, cursorIn, cursorOut, entities,
                limit, (after, pageable) -> attachmentsRepo.findChangesSince(tenantId, after.updatedAt(), after.id(), pageable),
                Allegato::getId, Allegato::getUpdatedAt, Allegato::getVersion, Allegato::isDeleted,
                AllegatoDto::from, attachment -> hasEntityReadPermission(attachment.getEntityType(), authorities));

        return new ModificheDto(encodeCursor(cursorOut), hasMore, entities);
    }

    /**
     * {@code hourlyCostOverride} va nascosto a chi non ha {@code labor-cost:read}, come già fa
     * {@code GET /api/commesse/{id}/resource-assignments} (ADR 0021): stessa regola qui.
     */
    private static Function<AssegnazioneRisorsa, Object> withoutHourlyCostOverrideUnless(boolean laborCostRead) {
        return a -> {
            AssegnazioneRisorsaDto dto = AssegnazioneRisorsaDto.from(a);
            return laborCostRead ? dto : dto.withoutHourlyCostOverride();
        };
    }

    /** Come l'overload con {@code visible}, ma senza filtro di riga: ogni riga letta è nella risposta. */
    private <T> boolean syncEntity(String entityName, Permesso readPermission, Set<String> authorities,
                                   Map<String, VoceCursore> cursorIn, Map<String, VoceCursore> cursorOut,
                                   Map<String, List<ModificaDto>> out, int limit, RichiestaModifiche<T> query,
                                   Function<T, UUID> idOf, Function<T, Instant> updatedAtOf,
                                   Function<T, Long> versionOf, Function<T, Boolean> deletedOf,
                                   Function<T, Object> dataOf) {
        return syncEntity(entityName, readPermission, authorities, cursorIn, cursorOut, out, limit, query, idOf,
                updatedAtOf, versionOf, deletedOf, dataOf, row -> true);
    }

    /**
     * Interroga, pagina e mappa un'entità, se l'utente ha il permesso di lettura corrispondente.
     * Richiede {@code limit + 1} righe per sapere se ce ne sono altre senza una {@code COUNT}
     * separata. Se non ci sono risultati il cursore dell'entità resta quello in ingresso: non
     * c'è nulla da far avanzare, e la prossima chiamata ripartirà dallo stesso punto.
     *
     * <p>{@code visible} filtra le singole righe (es. {@code attachments}, per il permesso della
     * loro {@code entityType}): il cursore avanza comunque fino all'ultima riga <strong>letta</strong>
     * (non solo quelle visibili), altrimenti una riga non visibile bloccherebbe per sempre
     * l'avanzamento di tutte quelle dopo di lei.
     *
     * @return vero se questa entità ha raggiunto il limite di pagina (altre modifiche in coda)
     */
    private <T> boolean syncEntity(String entityName, Permesso readPermission, Set<String> authorities,
                                   Map<String, VoceCursore> cursorIn, Map<String, VoceCursore> cursorOut,
                                   Map<String, List<ModificaDto>> out, int limit, RichiestaModifiche<T> query,
                                   Function<T, UUID> idOf, Function<T, Instant> updatedAtOf,
                                   Function<T, Long> versionOf, Function<T, Boolean> deletedOf,
                                   Function<T, Object> dataOf, Predicate<T> visible) {
        if (!authorities.contains(readPermission.code())) {
            return false;
        }
        VoceCursore after = cursorIn.getOrDefault(entityName, VoceCursore.BEGINNING);
        Pageable page = PageRequest.of(0, limit + 1);
        List<T> rows = query.fetch(after, page);

        boolean hasMore = rows.size() > limit;
        List<T> changed = hasMore ? rows.subList(0, limit) : rows;
        out.put(entityName, changed.stream()
                .filter(visible)
                .map(row -> new ModificaDto(idOf.apply(row), updatedAtOf.apply(row), versionOf.apply(row),
                        deletedOf.apply(row), deletedOf.apply(row) ? null : dataOf.apply(row)))
                .toList());
        cursorOut.put(entityName, changed.isEmpty() ? after
                : new VoceCursore(updatedAtOf.apply(changed.get(changed.size() - 1)),
                        idOf.apply(changed.get(changed.size() - 1))));
        return hasMore;
    }

    /** Permesso di lettura dell'entità a cui un allegato è appeso (vedi {@code AllegatoService}). */
    private static boolean hasEntityReadPermission(String entityType, Set<String> authorities) {
        PermessiEntitaDominio.PermessiEntita permissions = PermessiEntitaDominio.of(entityType);
        return permissions != null && authorities.contains(permissions.read().code());
    }

    private Map<String, VoceCursore> decodeCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(raw);
            return objectMapper.readValue(json, new TypeReference<Map<String, VoceCursore>>() {
            });
        } catch (IllegalArgumentException | IOException e) {
            throw new CursoreNonValidoException("Cursore di sincronizzazione non valido", e);
        }
    }

    private String encodeCursor(Map<String, VoceCursore> cursor) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(cursor));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile codificare il cursore di sincronizzazione", e);
        }
    }

    @FunctionalInterface
    private interface RichiestaModifiche<T> {
        List<T> fetch(VoceCursore after, Pageable pageable);
    }
}
