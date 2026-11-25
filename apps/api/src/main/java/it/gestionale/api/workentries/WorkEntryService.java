package it.gestionale.api.workentries;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.dailyreports.DailyReport;
import it.gestionale.api.dailyreports.DailyReportRepository;
import it.gestionale.api.resourceassignments.ResourceAssignmentRepository;
import it.gestionale.api.resources.ResourceRepository;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.workactivities.WorkActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkEntryService {

    private final WorkEntryRepository entries;
    private final DailyReportRepository dailyReports;
    private final ResourceRepository resources;
    private final WorkActivityRepository activities;
    private final ResourceAssignmentRepository assignments;
    private final Clock clock;

    public WorkEntryService(WorkEntryRepository entries, DailyReportRepository dailyReports,
                            ResourceRepository resources, WorkActivityRepository activities,
                            ResourceAssignmentRepository assignments, Clock clock) {
        this.entries = entries;
        this.dailyReports = dailyReports;
        this.resources = resources;
        this.activities = activities;
        this.assignments = assignments;
        this.clock = clock;
    }

    /** Righe di ore del rapportino, nell'ordine di inserimento: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<WorkEntryDto> listByDailyReport(UUID dailyReportId) {
        return entries
                .findByDailyReportIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(dailyReportId,
                        TenantContext.require())
                .stream().map(WorkEntryDto::from).toList();
    }

    /**
     * Crea o aggiorna la riga con l'id indicato (generato dal client). Idempotente: reinviare la
     * stessa operazione dalla outbox produce lo stesso risultato. Una riga cancellata logicamente
     * torna attiva (vedi {@link WorkEntry#update}).
     */
    @Transactional
    public WorkEntryDto upsert(UUID id, WorkEntryDto.Input input, String actorUserId) {
        UUID tenantId = TenantContext.require();
        UUID dailyReportId = input.dailyReportId();
        UUID resourceId = input.resourceId();
        UUID workActivityId = input.workActivityId();
        Optional<WorkEntry> existing = entries.findByIdAndTenantId(id, tenantId);

        // Come per commesse.customer_id: si rivalida solo quando il riferimento cambia davvero,
        // così una riga già collegata resta aggiornabile anche se il rapportino, la risorsa o la
        // lavorazione sono stati cancellati logicamente nel frattempo per altri motivi. Ma la
        // validità di lavorazione/assegnazione dipende dalla commessa e dalla data del
        // rapportino (come il padre di WorkActivity dipende dalla sua commessa): un cambio di
        // rapportino le rivalida comunque, anche se lavorazione o risorsa non cambiano.
        boolean dailyReportChanged = existing.map(e -> !e.getDailyReportId().equals(dailyReportId)).orElse(true);
        boolean resourceChanged = existing.map(e -> !e.getResourceId().equals(resourceId)).orElse(true);
        boolean workActivityChanged = existing.map(e -> !e.getWorkActivityId().equals(workActivityId)).orElse(true);

        DailyReport report = requireValidDailyReport(dailyReportId, tenantId, dailyReportChanged);
        if (resourceChanged) {
            requireValidResource(resourceId, tenantId);
        }
        if (dailyReportChanged || workActivityChanged) {
            requireValidWorkActivity(workActivityId, report.getCommessaId(), tenantId);
        }
        if (dailyReportChanged || resourceChanged) {
            requireValidAssignment(resourceId, report.getCommessaId(), report.getDate(), tenantId);
        }

        WorkEntry entry = existing
                .map(e -> {
                    e.update(dailyReportId, resourceId, workActivityId, input.hours(), input.overtimeHours(),
                            input.notes(), actorUserId, clock.instant());
                    return e;
                })
                .orElseGet(() -> entries.save(new WorkEntry(id, tenantId, dailyReportId, resourceId, workActivityId,
                        input.hours(), input.overtimeHours(), input.notes(), actorUserId)));
        entries.flush();
        return WorkEntryDto.from(entry);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        entries.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(entry -> entry.markDeleted(actorUserId, clock.instant()));
        entries.flush();
    }

    /** Il rapportino deve essere un'anagrafica attiva dello stesso tenant, sempre restituita per il suo id/data. */
    private DailyReport requireValidDailyReport(UUID dailyReportId, UUID tenantId, boolean mustBeActive) {
        Optional<DailyReport> found = dailyReports.findByIdAndTenantId(dailyReportId, tenantId);
        if (mustBeActive && found.map(DailyReport::isDeleted).orElse(true)) {
            throw new UnknownWorkEntryReferenceException("Rapportino non valido: " + dailyReportId);
        }
        return found.orElseThrow(
                () -> new UnknownWorkEntryReferenceException("Rapportino non valido: " + dailyReportId));
    }

    /** Difesa in profondità: la risorsa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidResource(UUID resourceId, UUID tenantId) {
        boolean active = resources.findByIdAndTenantId(resourceId, tenantId).map(r -> !r.isDeleted()).orElse(false);
        if (!active) {
            throw new UnknownWorkEntryReferenceException("Risorsa non valida: " + resourceId);
        }
    }

    /** La lavorazione deve essere attiva e appartenere alla stessa commessa del rapportino. */
    private void requireValidWorkActivity(UUID workActivityId, UUID commessaId, UUID tenantId) {
        boolean valid = activities.findByIdAndTenantId(workActivityId, tenantId)
                .map(a -> !a.isDeleted() && a.getCommessaId().equals(commessaId))
                .orElse(false);
        if (!valid) {
            throw new UnknownWorkEntryReferenceException(
                    "Lavorazione non valida per questa commessa: " + workActivityId);
        }
    }

    /** La risorsa deve avere un'assegnazione valida alla commessa nella data del rapportino. */
    private void requireValidAssignment(UUID resourceId, UUID commessaId, LocalDate date, UUID tenantId) {
        if (!assignments.existsValidAssignment(resourceId, commessaId, date, tenantId)) {
            throw new UnknownWorkEntryReferenceException(
                    "La risorsa non ha un'assegnazione valida a questa commessa in data " + date);
        }
    }
}
