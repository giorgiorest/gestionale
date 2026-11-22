package it.gestionale.api.licensing;

import java.time.Clock;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto unico di verifica degli entitlement del tenant corrente.
 * Ogni funzionalità a pagamento va controllata qui, nel backend.
 */
@Service
public class EntitlementService {

    private final LicenseRepository licenses;
    private final Clock clock;

    public EntitlementService(LicenseRepository licenses, Clock clock) {
        this.licenses = licenses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Entitlements current() {
        UUID tenantId = TenantContext.require();
        return licenses.findValid(tenantId, clock.instant()).stream()
                .findFirst()
                .map(License::toEntitlements)
                .orElseGet(Entitlements::none);
    }

    public boolean hasFeature(String feature) {
        return current().hasFeature(feature);
    }

    /** Lancia {@link FeatureNotEnabledException} se la funzionalità non è nel piano. */
    public Entitlements requireFeature(String feature) {
        Entitlements entitlements = current();
        if (!entitlements.hasFeature(feature)) {
            throw new FeatureNotEnabledException(feature);
        }
        return entitlements;
    }
}
