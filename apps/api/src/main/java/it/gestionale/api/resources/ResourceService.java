package it.gestionale.api.resources;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    private final ResourceRepository resources;
    private final Clock clock;

    public ResourceService(ResourceRepository resources, Clock clock) {
        this.resources = resources;
        this.clock = clock;
    }

    /** Risorse attive del tenant, ordinate per cognome e nome: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<ResourceDto> list() {
        return resources.findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(TenantContext.require())
                .stream().map(ResourceDto::from).toList();
    }

    /**
     * Crea o aggiorna la risorsa con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Una risorsa cancellata logicamente torna attiva (vedi {@link Resource#update}).
     */
    @Transactional
    public ResourceDto upsert(UUID id, ResourceDto.Input input, String actorUserId) {
        UUID tenantId = TenantContext.require();
        String firstName = input.firstName().trim();
        String lastName = input.lastName().trim();
        String userId = blankToNull(input.userId());
        UUID teamLeadId = requireValidTeamLead(input.teamLeadId(), id, tenantId);
        Resource resource = resources.findByIdAndTenantId(id, tenantId)
                .map(existing -> {
                    existing.update(firstName, lastName, userId, teamLeadId, actorUserId, clock.instant());
                    return existing;
                })
                .orElseGet(() -> resources.save(
                        new Resource(id, tenantId, firstName, lastName, userId, teamLeadId, actorUserId)));
        resources.flush();
        return ResourceDto.from(resource);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        resources.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(resource -> resource.markDeleted(actorUserId, clock.instant()));
        resources.flush();
    }

    /**
     * Difesa in profondità: il capo squadra deve essere una risorsa attiva dello stesso tenant, non
     * se stessa, e non deve risalire fino a {@code id} (altrimenti la squadra sarebbe circolare).
     */
    private UUID requireValidTeamLead(UUID teamLeadId, UUID id, UUID tenantId) {
        if (teamLeadId == null) {
            return null;
        }
        if (teamLeadId.equals(id) || !resources.existsByIdAndTenantIdAndDeletedAtIsNull(teamLeadId, tenantId)
                || createsCycle(teamLeadId, id, tenantId)) {
            throw new UnknownTeamLeadException("Capo squadra non valido: " + teamLeadId);
        }
        return teamLeadId;
    }

    /** Risale la catena dei capi squadra da {@code candidateLead}: tornare a {@code id} sarebbe un ciclo. */
    private boolean createsCycle(UUID candidateLead, UUID id, UUID tenantId) {
        long maxHops = resources.count();
        UUID current = candidateLead;
        for (long hops = 0; current != null && hops <= maxHops; hops++) {
            if (current.equals(id)) {
                return true;
            }
            current = resources.findByIdAndTenantId(current, tenantId).map(Resource::getTeamLeadId).orElse(null);
        }
        return false;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
