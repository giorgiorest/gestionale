package it.gestionale.api.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * Risposta di un {@link AiProvider}, con i dati per registrare consumo e costo.
 */
public record AiResponse(String content, List<AiToolCall> toolCalls, String model, int inputTokens,
                         int outputTokens, BigDecimal estimatedCostEur) {

    public AiResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
