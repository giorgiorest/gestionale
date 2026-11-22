package it.gestionale.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.licensing.License;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.Tenant;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantKind;
import it.gestionale.api.tenancy.TenantRepository;
import org.springframework.transaction.support.TransactionTemplate;

/** Crea tenant di test con la loro licenza, sempre nel contesto del tenant (RLS). */
public final class TestData {

    private TestData() {
    }

    public static UUID createTenant(TransactionTemplate tx, TenantRepository tenants, LicenseRepository licenses,
                                    String plan, Set<String> features, int aiQuota) {
        UUID tenantId = UUID.randomUUID();
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s -> {
            tenants.save(new Tenant(tenantId, "Tenant " + plan, "t-" + tenantId, TenantKind.CUSTOMER));
            licenses.save(new License(UUID.randomUUID(), tenantId, plan, 10, features, aiQuota,
                    Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(365, ChronoUnit.DAYS)));
        }));
        return tenantId;
    }
}
