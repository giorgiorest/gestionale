package it.gestionale.api.rapportini;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import it.gestionale.api.assegnazioni.AssegnazioneRisorsa;
import it.gestionale.api.assegnazioni.AssegnazioneRisorsaRepository;
import it.gestionale.api.lavorazioni.LavorazioneRepository;
import it.gestionale.api.risorse.Risorsa;
import it.gestionale.api.risorse.RisorsaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RigaOreService {

    private final RigaOreRepository entries;
    private final RapportinoRepository dailyReports;
    private final RisorsaRepository resources;
    private final LavorazioneRepository activities;
    private final AssegnazioneRisorsaRepository assignments;
    private final SnapshotCostoRigaOreRepository costSnapshots;
    private final Clock clock;

    public RigaOreService(RigaOreRepository entries, RapportinoRepository dailyReports,
                            RisorsaRepository resources, LavorazioneRepository activities,
                            AssegnazioneRisorsaRepository assignments,
                            SnapshotCostoRigaOreRepository costSnapshots, Clock clock) {
        this.entries = entries;
        this.dailyReports = dailyReports;
        this.resources = resources;
        this.activities = activities;
        this.assignments = assignments;
        this.costSnapshots = costSnapshots;
        this.clock = clock;
    }

    /** Righe di ore del rapportino, nell'ordine di inserimento: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<RigaOreDto> listByDailyReport(UUID dailyReportId) {
        return entries
                .findByDailyReportIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(dailyReportId,
                        ContestoTenant.require())
                .stream().map(RigaOreDto::from).toList();
    }

    /**
     * Crea o aggiorna la riga con l'id indicato (generato dal client). Idempotente: reinviare la
     * stessa operazione dalla outbox produce lo stesso risultato. Una riga cancellata logicamente
     * torna attiva (vedi {@link RigaOre#update}).
     */
    @Transactional
    public RigaOreDto upsert(UUID id, RigaOreDto.Input input, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        UUID dailyReportId = input.dailyReportId();
        UUID resourceId = input.resourceId();
        UUID workActivityId = input.workActivityId();
        Optional<RigaOre> existing = entries.findByIdAndTenantId(id, tenantId);

        // Come per commesse.customer_id: si rivalida solo quando il riferimento cambia davvero,
        // così una riga già collegata resta aggiornabile anche se il rapportino, la risorsa o la
        // lavorazione sono stati cancellati logicamente nel frattempo per altri motivi. Ma la
        // validità di lavorazione/assegnazione dipende dalla commessa e dalla data del
        // rapportino (come il padre di Lavorazione dipende dalla sua commessa): un cambio di
        // rapportino le rivalida comunque, anche se lavorazione o risorsa non cambiano.
        boolean dailyReportChanged = existing.map(e -> !e.getDailyReportId().equals(dailyReportId)).orElse(true);
        boolean resourceChanged = existing.map(e -> !e.getResourceId().equals(resourceId)).orElse(true);
        boolean workActivityChanged = existing.map(e -> !e.getWorkActivityId().equals(workActivityId)).orElse(true);

        Rapportino report = requireValidDailyReport(dailyReportId, tenantId, dailyReportChanged);
        // Un rapportino non più DRAFT non si può modificare nemmeno nelle sue righe (#51), anche
        // quando il riferimento al rapportino non cambia.
        report.requireDraft();
        if (resourceChanged) {
            requireValidResource(resourceId, tenantId);
        }
        if (dailyReportChanged || workActivityChanged) {
            requireValidWorkActivity(workActivityId, report.getCommessaId(), tenantId);
        }
        if (dailyReportChanged || resourceChanged) {
            requireValidAssignment(resourceId, report.getCommessaId(), report.getDate(), tenantId);
        }

        RigaOre entry = existing
                .map(e -> {
                    e.update(dailyReportId, resourceId, workActivityId, input.hours(), input.overtimeHours(),
                            input.notes(), actorUserId, clock.instant());
                    return e;
                })
                .orElseGet(() -> entries.save(new RigaOre(id, tenantId, dailyReportId, resourceId, workActivityId,
                        input.hours(), input.overtimeHours(), input.notes(), actorUserId)));
        entries.flush();
        return RigaOreDto.from(entry);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     * Una riga di un rapportino non più DRAFT non si può cancellare da qui (#51); una riga già
     * cancellata non fa il controllo, per non rompere il re-invio idempotente dalla outbox.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        entries.findByIdAndTenantId(id, tenantId).ifPresent(entry -> {
            if (!entry.isDeleted()) {
                dailyReports.findByIdAndTenantId(entry.getDailyReportId(), tenantId)
                        .ifPresent(Rapportino::requireDraft);
            }
            entry.markDeleted(actorUserId, clock.instant());
        });
        entries.flush();
    }

    /**
     * Snapshot del costo manodopera delle righe attive del rapportino, chiamato da
     * {@code RapportinoService.close()} nella stessa transazione della chiusura (#51): se quella
     * transazione va in rollback, anche questi inserimenti lo fanno. La tariffa oraria viene
     * prima dall'assegnazione valida della risorsa sulla commessa/data del rapportino (la stessa
     * che {@link #requireValidAssignment} ha già validato quando le righe sono state create); se
     * l'assegnazione non ha {@code hourlyCostOverride}, si ricade sulla tariffa oraria base della
     * risorsa ({@code Risorsa.standardHourlyCost}, #53). Il costo di quella riga resta sconosciuto
     * (NULL), non zero, solo se **nessuna delle due** esiste. Il fallback si applica una sola
     * volta, qui: lo snapshot è immutabile, quindi un cambio successivo della tariffa (override o
     * base) non lo altera mai — è così che il DoD ("il consuntivo chiuso non cambia se il costo
     * cambia dopo") è soddisfatto per costruzione. Lo straordinario usa la stessa tariffa delle
     * ore normali: non esiste ancora una maggiorazione definita.
     *
     * <p>Le assegnazioni e le risorse della commessa si leggono in una sola query ciascuna (non
     * una per riga): un rapportino con molte righe non deve costare una query per persona.
     */
    @Transactional
    public void snapshotCostsForClose(Rapportino report) {
        UUID tenantId = ContestoTenant.require();
        List<RigaOre> activeEntries = entries
                .findByDailyReportIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(report.getId(), tenantId);
        Map<UUID, List<AssegnazioneRisorsa>> assignmentsByResource = assignments
                .findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByValidFromAsc(report.getCommessaId(), tenantId)
                .stream().filter(AssegnazioneRisorsa::isActive)
                .collect(Collectors.groupingBy(AssegnazioneRisorsa::getResourceId));
        Map<UUID, Risorsa> resourcesById = resources
                .findAllById(activeEntries.stream().map(RigaOre::getResourceId).distinct().toList())
                .stream().collect(Collectors.toMap(Risorsa::getId, r -> r));
        List<SnapshotCostoRigaOre> snapshots = activeEntries.stream()
                .map(entry -> snapshotFor(report, entry, tenantId, assignmentsByResource, resourcesById))
                .toList();
        costSnapshots.saveAll(snapshots);
        costSnapshots.flush();
    }

    private SnapshotCostoRigaOre snapshotFor(Rapportino report, RigaOre entry, UUID tenantId,
            Map<UUID, List<AssegnazioneRisorsa>> assignmentsByResource, Map<UUID, Risorsa> resourcesById) {
        LocalDate date = report.getDate();
        BigDecimal assignmentOverride = assignmentsByResource.getOrDefault(entry.getResourceId(), List.of()).stream()
                .filter(a -> !a.getValidFrom().isAfter(date) && (a.getValidTo() == null || !a.getValidTo().isBefore(date)))
                .max(Comparator.comparing(AssegnazioneRisorsa::getValidFrom))
                .map(AssegnazioneRisorsa::getHourlyCostOverride)
                .orElse(null);
        BigDecimal standardCost = Optional.ofNullable(resourcesById.get(entry.getResourceId()))
                .map(Risorsa::getStandardHourlyCost).orElse(null);
        BigDecimal hourlyCost = assignmentOverride != null ? assignmentOverride : standardCost;
        BigDecimal laborCost = hourlyCost == null ? null
                : entry.getHours().add(entry.getOvertimeHours()).multiply(hourlyCost);
        return new SnapshotCostoRigaOre(UUID.randomUUID(), tenantId, report.getId(), entry.getId(),
                entry.getResourceId(), entry.getWorkActivityId(), entry.getHours(), entry.getOvertimeHours(),
                hourlyCost, laborCost);
    }

    /** Il rapportino deve essere un'anagrafica attiva dello stesso tenant, sempre restituita per il suo id/data. */
    private Rapportino requireValidDailyReport(UUID dailyReportId, UUID tenantId, boolean mustBeActive) {
        Optional<Rapportino> found = dailyReports.findByIdAndTenantId(dailyReportId, tenantId);
        if (mustBeActive && found.map(Rapportino::isDeleted).orElse(true)) {
            throw new RiferimentoRigaOreSconosciutoException("Rapportino non valido: " + dailyReportId);
        }
        return found.orElseThrow(
                () -> new RiferimentoRigaOreSconosciutoException("Rapportino non valido: " + dailyReportId));
    }

    /** Difesa in profondità: la risorsa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidResource(UUID resourceId, UUID tenantId) {
        boolean active = resources.findByIdAndTenantId(resourceId, tenantId).map(r -> !r.isDeleted()).orElse(false);
        if (!active) {
            throw new RiferimentoRigaOreSconosciutoException("Risorsa non valida: " + resourceId);
        }
    }

    /** La lavorazione deve essere attiva e appartenere alla stessa commessa del rapportino. */
    private void requireValidWorkActivity(UUID workActivityId, UUID commessaId, UUID tenantId) {
        boolean valid = activities.findByIdAndTenantId(workActivityId, tenantId)
                .map(a -> !a.isDeleted() && a.getCommessaId().equals(commessaId))
                .orElse(false);
        if (!valid) {
            throw new RiferimentoRigaOreSconosciutoException(
                    "Lavorazione non valida per questa commessa: " + workActivityId);
        }
    }

    /** La risorsa deve avere un'assegnazione valida alla commessa nella data del rapportino. */
    private void requireValidAssignment(UUID resourceId, UUID commessaId, LocalDate date, UUID tenantId) {
        if (!assignments.existsValidAssignment(resourceId, commessaId, date, tenantId)) {
            throw new RiferimentoRigaOreSconosciutoException(
                    "La risorsa non ha un'assegnazione valida a questa commessa in data " + date);
        }
    }
}
