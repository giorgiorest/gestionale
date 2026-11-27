package it.gestionale.api.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Provider di default: nessun modello configurato, risponde sempre 503. */
@Component
@ConditionalOnProperty(name = "gestionale.ai.provider", havingValue = "none", matchIfMissing = true)
public class ProviderIaNullo implements ProviderIa {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public RispostaIa chat(RichiestaIa request) {
        throw new IaNonDisponibileException("Assistente AI non configurato");
    }
}
