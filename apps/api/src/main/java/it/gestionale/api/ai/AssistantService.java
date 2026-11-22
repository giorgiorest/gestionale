package it.gestionale.api.ai;

import java.util.List;

import it.gestionale.api.licensing.EntitlementService;
import it.gestionale.api.licensing.Features;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Orchestrazione dell'assistente: entitlement, quota, chiamata al provider, registrazione del consumo.
 * L'esecuzione dei tool proposti dal modello (con conferma per le scritture) verrà aggiunta qui.
 */
@Service
public class AssistantService {

    private final EntitlementService entitlementService;
    private final AiQuotaService quotaService;
    private final AiProvider provider;
    private final List<AiTool> tools;

    public AssistantService(EntitlementService entitlementService, AiQuotaService quotaService, AiProvider provider,
                            List<AiTool> tools) {
        this.entitlementService = entitlementService;
        this.quotaService = quotaService;
        this.provider = provider;
        this.tools = List.copyOf(tools);
    }

    public AiResponse chat(String userId, List<AiMessage> messages) {
        entitlementService.requireFeature(Features.AI_ASSISTANT);
        quotaService.checkQuota();
        AiResponse response = provider.chat(new AiRequest(TenantContext.require(), userId, messages, tools));
        quotaService.recordUsage(userId, response);
        return response;
    }
}
