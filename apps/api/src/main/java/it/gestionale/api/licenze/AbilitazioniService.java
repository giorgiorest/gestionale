package it.gestionale.api.licenze;

import java.time.Clock;
import java.util.UUID;

import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto unico di verifica degli entitlement del tenant corrente.
 * Ogni funzionalità a pagamento va controllata qui, nel backend.
 */
@Service
public class AbilitazioniService {

    private final LicenzaRepository licenses;
    private final Clock clock;

    public AbilitazioniService(LicenzaRepository licenses, Clock clock) {
        this.licenses = licenses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Abilitazioni current() {
        UUID tenantId = ContestoTenant.require();
        return licenses.findValid(tenantId, clock.instant()).stream()
                .findFirst()
                .map(Licenza::toEntitlements)
                .orElseGet(Abilitazioni::none);
    }

    public boolean hasFeature(String feature) {
        return current().hasFeature(feature);
    }

    /** Lancia {@link FunzionalitaNonAbilitataException} se la funzionalità non è nel piano. */
    public Abilitazioni requireFeature(String feature) {
        Abilitazioni entitlements = current();
        if (!entitlements.hasFeature(feature)) {
            throw new FunzionalitaNonAbilitataException(feature);
        }
        return entitlements;
    }
}
