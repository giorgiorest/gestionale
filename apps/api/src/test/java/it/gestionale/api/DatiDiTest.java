package it.gestionale.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.licenze.Licenza;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.Tenant;
import it.gestionale.api.tenant.TenantRepository;
import it.gestionale.api.tenant.TipoTenant;
import org.springframework.transaction.support.TransactionTemplate;

/** Crea tenant di test con la loro licenza, sempre nel contesto del tenant (RLS). */
public final class DatiDiTest {

    private DatiDiTest() {
    }

    public static UUID createTenant(TransactionTemplate tx, TenantRepository tenants, LicenzaRepository licenses,
                                    String plan, Set<String> features, int aiQuota) {
        UUID tenantId = UUID.randomUUID();
        ContestoTenant.runAs(tenantId, () -> tx.executeWithoutResult(s -> {
            tenants.save(new Tenant(tenantId, "Tenant " + plan, "t-" + tenantId, TipoTenant.CUSTOMER));
            licenses.save(new Licenza(UUID.randomUUID(), tenantId, plan, 10, features, aiQuota,
                    Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(365, ChronoUnit.DAYS)));
        }));
        return tenantId;
    }
}
