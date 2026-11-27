package it.gestionale.api.ia;

import java.math.BigDecimal;
import java.util.List;

/**
 * Risposta di un {@link ProviderIa}, con i dati per registrare consumo e costo.
 */
public record RispostaIa(String content, List<ChiamataStrumentoIa> toolCalls, String model, int inputTokens,
                         int outputTokens, BigDecimal estimatedCostEur) {

    public RispostaIa {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
