package it.gestionale.api.workactivities;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkActivityService {

    private final WorkActivityRepository activities;
    private final CommessaRepository commesse;
    private final Clock clock;

    public WorkActivityService(WorkActivityRepository activities, CommessaRepository commesse, Clock clock) {
        this.activities = activities;
        this.commesse = commesse;
        this.clock = clock;
    }

    /** Lavorazioni attive della commessa, per codice: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<WorkActivityDto> listByCommessa(UUID commessaId) {
        return activities
                .findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByCodeAsc(commessaId, TenantContext.require())
                .stream().map(WorkActivityDto::from).toList();
    }

    /**
     * Crea o aggiorna la lavorazione con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Una lavorazione cancellata logicamente torna attiva (vedi {@link WorkActivity#update}).
     */
    @Transactional
    public WorkActivityDto upsert(UUID id, WorkActivityDto.Input input, String actorUserId) {
        UUID tenantId = TenantContext.require();
        UUID commessaId = input.commessaId();
        UUID parentId = input.parentId();
        Optional<WorkActivity> existing = activities.findByIdAndTenantId(id, tenantId);
        // Come per commesse.customer_id: si valida solo alla creazione o quando il riferimento
        // cambia davvero, così un record già collegato resta aggiornabile anche se la commessa o
        // il padre sono stati cancellati logicamente nel frattempo per altri motivi. Un cambio di
        // commessa però invalida anche un parentId non cambiato: il padre deve appartenere alla
        // commessa *nuova*, non solo esistere, quindi il padre va rivalidato anche in quel caso.
        boolean commessaChanged = existing.map(WorkActivity::getCommessaId).map(c -> !c.equals(commessaId))
                .orElse(true);
        boolean parentChanged = existing.map(WorkActivity::getParentId)
                .map(p -> !java.util.Objects.equals(p, parentId)).orElse(true);
        if (commessaChanged) {
            requireValidCommessa(commessaId, tenantId);
        }
        if (commessaChanged || parentChanged) {
            requireValidParent(parentId, id, commessaId, tenantId);
        }
        String code = input.code().trim();
        String name = input.name().trim();
        String description = input.description() == null || input.description().isBlank()
                ? null : input.description().trim();
        WorkActivityStatus status = WorkActivityStatus.valueOf(input.status().toUpperCase(Locale.ROOT));
        WorkActivity activity = existing
                .map(a -> {
                    a.update(commessaId, parentId, code, name, description, status, input.progressPercentage(),
                            actorUserId, clock.instant());
                    return a;
                })
                .orElseGet(() -> activities.save(new WorkActivity(id, tenantId, commessaId, parentId, code, name,
                        description, status, input.progressPercentage(), actorUserId)));
        activities.flush();
        return WorkActivityDto.from(activity);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        activities.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(activity -> activity.markDeleted(actorUserId, clock.instant()));
        activities.flush();
    }

    /** Difesa in profondità: la commessa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidCommessa(UUID commessaId, UUID tenantId) {
        boolean active = commesse.findByIdAndTenantId(commessaId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new UnknownWorkActivityReferenceException("Commessa non valida: " + commessaId);
        }
    }

    /**
     * Il padre (WBS), se indicato, deve essere una lavorazione attiva della stessa commessa e
     * dello stesso tenant, e non la lavorazione stessa.
     */
    private void requireValidParent(UUID parentId, UUID id, UUID commessaId, UUID tenantId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(id)) {
            throw new UnknownWorkActivityReferenceException("Una lavorazione non può essere padre di se stessa");
        }
        boolean valid = activities.findByIdAndTenantId(parentId, tenantId)
                .map(p -> !p.isDeleted() && p.getCommessaId().equals(commessaId))
                .orElse(false);
        if (!valid) {
            throw new UnknownWorkActivityReferenceException("Lavorazione padre non valida: " + parentId);
        }
    }
}
