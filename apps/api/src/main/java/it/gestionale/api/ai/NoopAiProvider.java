package it.gestionale.api.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Provider di default: nessun modello configurato, risponde sempre 503. */
@Component
@ConditionalOnProperty(name = "gestionale.ai.provider", havingValue = "none", matchIfMissing = true)
public class NoopAiProvider implements AiProvider {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public AiResponse chat(AiRequest request) {
        throw new AiUnavailableException("Assistente AI non configurato");
    }
}
