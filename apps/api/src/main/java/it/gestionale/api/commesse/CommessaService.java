package it.gestionale.api.commesse;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommessaService {

    private final CommessaRepository commesse;
    private final ClienteRepository customers;
    private final Clock clock;

    public CommessaService(CommessaRepository commesse, ClienteRepository customers, Clock clock) {
        this.commesse = commesse;
        this.customers = customers;
        this.clock = clock;
    }

    /** Commesse attive del tenant, ordinate per nome: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<CommessaDto> list() {
        return commesse.findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(ContestoTenant.require())
                .stream().map(CommessaDto::from).toList();
    }

    /**
     * Crea o aggiorna la commessa con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Una commessa cancellata logicamente torna attiva (vedi {@link Commessa#update}).
     */
    @Transactional
    public CommessaDto upsert(UUID id, CommessaDto.Input input, String actorUserId) {
        UUID tenantId = ContestoTenant.require();
        UUID customerId = input.customerId();
        Optional<Commessa> existing = commesse.findByIdAndTenantId(id, tenantId);
        // Un cliente già collegato resta valido anche se cancellato logicamente nel frattempo: non
        // deve bloccare un aggiornamento (es. chiudere lo stato) che non lo riguarda. Si valida solo
        // alla creazione o quando il cliente cambia davvero.
        if (existing.map(Commessa::getCustomerId).map(c -> !c.equals(customerId)).orElse(true)) {
            requireValidCustomer(customerId, tenantId);
        }
        String nome = input.nome().trim();
        String indirizzo = input.indirizzo() == null || input.indirizzo().isBlank() ? null : input.indirizzo().trim();
        StatoCommessa stato = StatoCommessa.valueOf(input.stato().toUpperCase(Locale.ROOT));
        Commessa commessa = existing
                .map(c -> {
                    c.update(customerId, nome, indirizzo, stato, actorUserId, clock.instant());
                    return c;
                })
                .orElseGet(() -> commesse.save(
                        new Commessa(id, tenantId, customerId, nome, indirizzo, stato, actorUserId)));
        commesse.flush();
        return CommessaDto.from(commessa);
    }

    /**
     * Cancellazione logica, idempotente: il record resta come tombstone per la sincronizzazione.
     * Un id sconosciuto (o di un altro tenant) non è un errore: la outbox deve poter svuotarsi.
     */
    @Transactional
    public void delete(UUID id, String actorUserId) {
        commesse.findByIdAndTenantId(id, ContestoTenant.require())
                .ifPresent(commessa -> commessa.markDeleted(actorUserId, clock.instant()));
        commesse.flush();
    }

    /** Difesa in profondità: il cliente deve essere un'anagrafica attiva dello stesso tenant. */
    private void requireValidCustomer(UUID customerId, UUID tenantId) {
        boolean active = customers.findByIdAndTenantId(customerId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new ClienteSconosciutoException("Cliente non valido: " + customerId);
        }
    }
}
