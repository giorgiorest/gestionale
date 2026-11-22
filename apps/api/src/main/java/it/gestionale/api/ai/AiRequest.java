package it.gestionale.api.ai;

import java.util.List;
import java.util.UUID;

/**
 * Richiesta verso un {@link AiProvider}.
 *
 * @param tenantId tenant per cui si esegue la chiamata
 * @param userId   utente autenticato che ha avviato la conversazione
 * @param messages conversazione, solo con i dati necessari al task
 * @param tools    tool che il modello può proporre di invocare
 */
public record AiRequest(UUID tenantId, String userId, List<AiMessage> messages, List<AiTool> tools) {

    public AiRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
