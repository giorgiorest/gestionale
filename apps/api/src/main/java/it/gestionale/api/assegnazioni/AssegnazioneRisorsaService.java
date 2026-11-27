package it.gestionale.api.assegnazioni;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.risorse.RisorsaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssegnazioneRisorsaService {

    private final AssegnazioneRisorsaRepository assignments;
    private final RisorsaRepository resources;
    private final CommessaRepository commesse;
    private final Clock clock;

    public AssegnazioneRisorsaService(AssegnazioneRisorsaRepository assignments, RisorsaRepository resources,
                                     CommessaRepository commesse, Clock clock) {
        this.assignments = assignments;
        this.resources = resources;
        this.commesse = commesse;
        this.clock = clock;
    }

    /** Assegnazioni attive della commessa, in ordine di inizio: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<AssegnazioneRisorsaDto> listByCommessa(UUID commessaId) {
        return assignments
                .findByCommessaIdAndTenantIdAndDeletedAtIsNullOrderByValidFromAsc(commessaId, ContestoTenant.require())
                .stream().map(AssegnazioneRisorsaDto::from).toList();
    }

    /**
     * Crea o aggiorna l'assegnazione con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Un'assegnazione cancellata logicamente torna attiva (vedi {@link AssegnazioneRisorsa#update}).
     */
    @Transactional
    public AssegnazioneRisorsaDto upsert(UUID id, AssegnazioneRisorsaDto.Input input, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        UUID resourceId = input.resourceId();
        UUID commessaId = input.commessaId();
        Optional<AssegnazioneRisorsa> existing = assignments.findByIdAndTenantId(id, tenantId);
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
        AssegnazioneRisorsa assignment = existing
                .map(a -> {
                    a.update(resourceId, commessaId, input.validFrom(), input.validTo(), role,
                            input.hourlyCostOverride(), input.active(), actorUserId, clock.instant());
                    return a;
                })
                .orElseGet(() -> assignments.save(new AssegnazioneRisorsa(id, tenantId, resourceId, commessaId,
                        input.validFrom(), input.validTo(), role, input.hourlyCostOverride(), input.active(),
                        actorUserId)));
        assignments.flush();
        return AssegnazioneRisorsaDto.from(assignment);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        assignments.findByIdAndTenantId(id, ContestoTenant.require())
                .ifPresent(assignment -> assignment.markDeleted(actorUserId, clock.instant()));
        assignments.flush();
    }

    /** Difesa in profondità: la risorsa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidResource(UUID resourceId, UUID tenantId) {
        boolean active = resources.findByIdAndTenantId(resourceId, tenantId).map(r -> !r.isDeleted()).orElse(false);
        if (!active) {
            throw new RiferimentoAssegnazioneSconosciutoException("Risorsa non valida: " + resourceId);
        }
    }

    /** Difesa in profondità: la commessa deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidCommessa(UUID commessaId, UUID tenantId) {
        boolean active = commesse.findByIdAndTenantId(commessaId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new RiferimentoAssegnazioneSconosciutoException("Commessa non valida: " + commessaId);
        }
    }
}
