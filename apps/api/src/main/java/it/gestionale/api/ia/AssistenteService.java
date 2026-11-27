package it.gestionale.api.ia;

import java.util.List;

import it.gestionale.api.licenze.AbilitazioniService;
import it.gestionale.api.licenze.Funzionalita;
import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;

/**
 * Orchestrazione dell'assistente: entitlement, quota, chiamata al provider, registrazione del consumo.
 * L'esecuzione dei tool proposti dal modello (con conferma per le scritture) verrà aggiunta qui.
 */
@Service
public class AssistenteService {

    private final AbilitazioniService entitlementService;
    private final QuotaIaService quotaService;
    private final ProviderIa provider;
    private final List<StrumentoIa> tools;

    public AssistenteService(AbilitazioniService entitlementService, QuotaIaService quotaService, ProviderIa provider,
                            List<StrumentoIa> tools) {
        this.entitlementService = entitlementService;
        this.quotaService = quotaService;
        this.provider = provider;
        this.tools = List.copyOf(tools);
    }

    public RispostaIa chat(String userId, List<MessaggioIa> messages) {
        entitlementService.requireFeature(Funzionalita.AI_ASSISTANT);
        quotaService.checkQuota();
        RispostaIa response = provider.chat(new RichiestaIa(ContestoTenant.require(), userId, messages, tools));
        quotaService.recordUsage(userId, response);
        return response;
    }
}
