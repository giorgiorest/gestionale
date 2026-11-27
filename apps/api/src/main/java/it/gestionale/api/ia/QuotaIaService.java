package it.gestionale.api.ia;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import it.gestionale.api.licenze.Abilitazioni;
import it.gestionale.api.licenze.AbilitazioniService;
import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quota mensile dell'assistente, letta da {@link Abilitazioni#aiQuotaMonthly()}.
 * Unità: numero di richieste nel mese solare (UTC).
 */
@Service
public class QuotaIaService {

    private final AbilitazioniService entitlementService;
    private final UtilizzoIaRepository usageRepository;
    private final Clock clock;

    public QuotaIaService(AbilitazioniService entitlementService, UtilizzoIaRepository usageRepository, Clock clock) {
        this.entitlementService = entitlementService;
        this.usageRepository = usageRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public long usedThisMonth() {
        UUID tenantId = ContestoTenant.require();
        Instant monthStart = YearMonth.now(clock.withZone(ZoneOffset.UTC)).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return usageRepository.countByTenantIdAndCreatedAtGreaterThanEqual(tenantId, monthStart);
    }

    /** @throws QuotaIaSuperataException se le richieste del mese hanno raggiunto la quota */
    public void checkQuota() {
        int quota = entitlementService.current().aiQuotaMonthly();
        long used = usedThisMonth();
        if (used >= quota) {
            throw new QuotaIaSuperataException(used, quota);
        }
    }

    @Transactional
    public void recordUsage(String userId, RispostaIa response) {
        usageRepository.save(new UtilizzoIa(ContestoTenant.require(), userId, response, clock.instant()));
    }
}
