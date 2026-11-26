package it.gestionale.api.suppliers;

import java.time.Clock;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.web.PageDto;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;
    private final Clock clock;

    public SupplierService(SupplierRepository suppliers, Clock clock) {
        this.suppliers = suppliers;
        this.clock = clock;
    }

    /** Fornitori attivi del tenant (ADR 0014): i record cancellati logicamente non compaiono. */
    @Transactional(readOnly = true)
    public PageDto<SupplierDto> list(Pageable pageable, String q) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim() + "%";
        return PageDto.of(suppliers.search(TenantContext.require(), pattern, pageable), SupplierDto::from);
    }

    /**
     * Crea o aggiorna il fornitore con l'id indicato (generato dal client).
     * Idempotente: reinviare la stessa operazione dalla outbox produce lo stesso risultato.
     */
    @Transactional
    public SupplierDto upsert(UUID id, SupplierDto.Input input, String userId) {
        UUID tenantId = TenantContext.require();
        String name = input.name().trim();
        String email = blankToNull(input.email());
        String partitaIva = blankToNull(input.partitaIva());
        String codiceFiscale = blankToNull(input.codiceFiscale());
        String indirizzo = blankToNull(input.indirizzo());
        String pec = blankToNull(input.pec());
        String codiceSdi = blankToNull(input.codiceSdi());
        String condizioniPagamento = blankToNull(input.condizioniPagamento());
        Supplier supplier = suppliers.findByIdAndTenantId(id, tenantId)
                .map(existing -> {
                    existing.update(name, email, partitaIva, codiceFiscale, indirizzo, pec, codiceSdi,
                            condizioniPagamento, userId, clock.instant());
                    return existing;
                })
                .orElseGet(() -> suppliers.save(new Supplier(id, tenantId, name, email, partitaIva, codiceFiscale,
                        indirizzo, pec, codiceSdi, condizioniPagamento, userId)));
        suppliers.flush();
        return SupplierDto.from(supplier);
    }

    /** Cancellazione logica, idempotente: come {@code CustomerService.delete}. */
    @Transactional
    public void delete(UUID id, String userId) {
        suppliers.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(supplier -> supplier.markDeleted(userId, clock.instant()));
        suppliers.flush();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
