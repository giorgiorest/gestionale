package it.gestionale.api.risorse;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RisorsaService {

    private final RisorsaRepository resources;
    private final Clock clock;

    public RisorsaService(RisorsaRepository resources, Clock clock) {
        this.resources = resources;
        this.clock = clock;
    }

    /** Risorse attive del tenant, ordinate per cognome e nome: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<RisorsaDto> list() {
        return resources.findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(ContestoTenant.require())
                .stream().map(RisorsaDto::from).toList();
    }

    /**
     * Crea o aggiorna la risorsa con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Una risorsa cancellata logicamente torna attiva (vedi {@link Risorsa#update}).
     */
    @Transactional
    public RisorsaDto upsert(UUID id, RisorsaDto.Input input, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        String firstName = input.firstName().trim();
        String lastName = input.lastName().trim();
        // Non validato: un userId che non corrisponde a nessuna riga di app_users è legittimo quanto
        // un errore di digitazione (un neoassunto non ha ancora effettuato il primo accesso), e i due
        // casi non si possono distinguere. La RLS impedisce comunque che possa mai risolversi a una
        // persona di un altro tenant.
        String userId = blankToNull(input.userId());
        UUID teamLeadId = requireValidTeamLead(input.teamLeadId(), id, tenantId);
        Risorsa resource = resources.findByIdAndTenantId(id, tenantId)
                .map(existing -> {
                    existing.update(firstName, lastName, userId, teamLeadId, actorUserId, clock.instant());
                    return existing;
                })
                .orElseGet(() -> resources.save(
                        new Risorsa(id, tenantId, firstName, lastName, userId, teamLeadId, actorUserId)));
        resources.flush();
        return RisorsaDto.from(resource);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        resources.findByIdAndTenantId(id, ContestoTenant.require())
                .ifPresent(resource -> resource.markDeleted(actorUserId, clock.instant()));
        resources.flush();
    }

    /** Tariffa oraria base della risorsa (#53), dietro il permesso dedicato {@code labor-cost:read}. */
    @Transactional(readOnly = true)
    public CostoManodoperaRisorsaDto getLaborCost(UUID id) {
        return CostoManodoperaRisorsaDto.from(requireActiveResource(id));
    }

    /**
     * Imposta la tariffa oraria base (#53), dietro il permesso dedicato {@code labor-cost:write}.
     * Non idempotente in senso outbox (non è un'entità sincronizzata dal client): un id
     * inesistente o di un altro tenant è un errore (404), non un no-op.
     */
    @Transactional
    public CostoManodoperaRisorsaDto updateLaborCost(UUID id, CostoManodoperaRisorsaDto.Input input, String actorUserId) {
        Risorsa resource = requireActiveResource(id);
        resource.updateStandardHourlyCost(input.standardHourlyCost(), actorUserId, clock.instant());
        resources.flush();
        return CostoManodoperaRisorsaDto.from(resource);
    }

    private Risorsa requireActiveResource(UUID id) {
        return resources.findByIdAndTenantId(id, ContestoTenant.require())
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new RisorsaSconosciutaException("Risorsa non valida: " + id));
    }

    /**
     * Le squadre di cantiere sono a un solo livello (capo e membri, mai catene): il capo squadra deve
     * essere una risorsa attiva dello stesso tenant, non se stessa, non a sua volta membro di
     * un'altra squadra, e chi diventa membro non deve già avere una propria squadra. Così un ciclo
     * non è mai rappresentabile (a differenza di una catena libera, dove impedirlo richiederebbe di
     * risalirla a ogni scrittura, con il rischio di corse tra scritture concorrenti).
     */
    private UUID requireValidTeamLead(UUID teamLeadId, UUID id, UUID tenantId) {
        if (teamLeadId == null) {
            return null;
        }
        Risorsa lead = teamLeadId.equals(id) ? null : resources.findByIdAndTenantId(teamLeadId, tenantId)
                .filter(r -> !r.isDeleted() && r.getTeamLeadId() == null).orElse(null);
        if (lead == null || resources.existsByTeamLeadIdAndTenantIdAndDeletedAtIsNull(id, tenantId)) {
            throw new CapoSquadraSconosciutoException("Capo squadra non valido: " + teamLeadId);
        }
        return teamLeadId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
