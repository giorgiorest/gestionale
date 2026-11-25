package it.gestionale.api.resourceassignments;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.resources.ResourceRepository;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceAssignmentService {

    private final ResourceAssignmentRepository assignments;
    private final ResourceRepository resources;
    private final CommessaRepository commesse;
    private final Clock clock;

    public ResourceAssignmentService(ResourceAssignmentRepository assignments, ResourceRepository resources,
                                     CommessaRepository commesse, Clock clock) {
        this.assignments = assignments;
        this.resources = resources;
        this.commesse = commesse;
        this.clock = clock;
    }

    /** Assegnazioni attive della commessa, in ordine di inizio: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<ResourceAssignmentDto> listByCommessa(UUID commessaId) {
        return assignments
                .findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByValidFromAsc(commessaId, TenantContext.require())
                .stream().map(ResourceAssignmentDto::from).toList();
    }

    /**
     * Crea o aggiorna l'assegnazione con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Un'assegnazione cancellata logicamente torna attiva (vedi {@link ResourceAssignment#update}).
     */
    @Transactional
    public ResourceAssignmentDto upsert(UUID id, ResourceAssignmentDto.Input input, String actorUserId) {
        UUID tenantId = TenantContext.require();
        UUID resourceId = input.resourceId();
        UUID commessaId = input.commessaId();
        Optional<ResourceAssignment> existing = assignments.findByIdAndTenantId(id, tenantId);
        // Come per commesse.customer_id: si valida solo alla creazione o quando il riferimento
        // cambia davvero, così un record già collegato resta aggiornabile anche se la risorsa o la
        // commessa sono state cancellate logicamente nel frattempo per altri motivi.
        boolean referencesChanged = existing
                .map(a -> !a.getResourceId().equals(resourceId) || !a.getCommessaId().equals(commessaId))
                .orElse(true);
        if (referencesChanged) {
            requireValidResource(resourceId, tenantId);
            requireValidCommessa(commessaId, tenantId);
        }
        String role = input.role().trim();
        ResourceAssignment assignment = existing
                .map(a -> {
                    a.update(resourceId, commessaId, input.validFrom(), input.validTo(), role,
                            input.hourlyCostOverride(), input.active(), actorUserId, clock.instant());
                    return a;
                })
                .orElseGet(() -> assignments.save(new ResourceAssignment(id, tenantId, resourceId, commessaId,
                        input.validFrom(), input.validTo(), role, input.hourlyCostOverride(), input.active(),
                        actorUserId)));
        assignments.flush();
        return ResourceAssignmentDto.from(assignment);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        assignments.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(assignment -> assignment.markDeleted(actorUserId, clock.instant()));
        assignments.flush();
    }

    /** Difesa in profondità: la risorsa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidResource(UUID resourceId, UUID tenantId) {
        boolean active = resources.findByIdAndTenantId(resourceId, tenantId).map(r -> !r.isDeleted()).orElse(false);
        if (!active) {
            throw new UnknownAssignmentReferenceException("Risorsa non valida: " + resourceId);
        }
    }

    /** Difesa in profondità: la commessa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidCommessa(UUID commessaId, UUID tenantId) {
        boolean active = commesse.findByIdAndTenantId(commessaId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new UnknownAssignmentReferenceException("Commessa non valida: " + commessaId);
        }
    }
}
