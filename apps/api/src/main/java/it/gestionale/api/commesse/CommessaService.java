package it.gestionale.api.commesse;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommessaService {

    private final CommessaRepository commesse;
    private final CustomerRepository customers;
    private final Clock clock;

    public CommessaService(CommessaRepository commesse, CustomerRepository customers, Clock clock) {
        this.commesse = commesse;
        this.customers = customers;
        this.clock = clock;
    }

    /** Commesse attive del tenant, ordinate per nome: i record cancellati non compaiono. */
    @Transactional(readOnly = true)
    public List<CommessaDto> list() {
        return commesse.findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(TenantContext.require())
                .stream().map(CommessaDto::from).toList();
    }

    /**
     * Crea o aggiorna la commessa con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     * Una commessa cancellata logicamente torna attiva (vedi {@link Commessa#update}).
     */
    @Transactional
    public CommessaDto upsert(UUID id, CommessaDto.Input input, String actorUserId) {
        UUID tenantId = TenantContext.require();
        UUID customerId = requireValidCustomer(input.customerId(), tenantId);
        String nome = input.nome().trim();
        String indirizzo = input.indirizzo() == null || input.indirizzo().isBlank() ? null : input.indirizzo().trim();
        CommessaStato stato = CommessaStato.valueOf(input.stato().toUpperCase(java.util.Locale.ROOT));
        Commessa commessa = commesse.findByIdAndTenantId(id, tenantId)
                .map(existing -> {
                    existing.update(customerId, nome, indirizzo, stato, actorUserId, clock.instant());
                    return existing;
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
        commesse.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(commessa -> commessa.markDeleted(actorUserId, clock.instant()));
        commesse.flush();
    }

    /** Difesa in profondità: il cliente deve essere un'anagrafica attiva dello stesso tenant. */
    private UUID requireValidCustomer(UUID customerId, UUID tenantId) {
        boolean active = customers.findByIdAndTenantId(customerId, tenantId).map(c -> !c.isDeleted()).orElse(false);
        if (!active) {
            throw new UnknownCustomerException("Cliente non valido: " + customerId);
        }
        return customerId;
    }
}
