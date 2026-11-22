package it.gestionale.api.ai;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import it.gestionale.api.licensing.Entitlements;
import it.gestionale.api.licensing.EntitlementService;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quota mensile dell'assistente, letta da {@link Entitlements#aiQuotaMonthly()}.
 * Unità: numero di richieste nel mese solare (UTC).
 */
@Service
public class AiQuotaService {

    private final EntitlementService entitlementService;
    private final AiUsageRepository usageRepository;
    private final Clock clock;

    public AiQuotaService(EntitlementService entitlementService, AiUsageRepository usageRepository, Clock clock) {
        this.entitlementService = entitlementService;
        this.usageRepository = usageRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public long usedThisMonth() {
        UUID tenantId = TenantContext.require();
        Instant monthStart = YearMonth.now(clock.withZone(ZoneOffset.UTC)).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return usageRepository.countByTenantIdAndCreatedAtGreaterThanEqual(tenantId, monthStart);
    }

    /** @throws AiQuotaExceededException se le richieste del mese hanno raggiunto la quota */
    public void checkQuota() {
        int quota = entitlementService.current().aiQuotaMonthly();
        long used = usedThisMonth();
        if (used >= quota) {
            throw new AiQuotaExceededException(used, quota);
        }
    }

    @Transactional
    public void recordUsage(String userId, AiResponse response) {
        usageRepository.save(new AiUsage(TenantContext.require(), userId, response, clock.instant()));
    }
}
