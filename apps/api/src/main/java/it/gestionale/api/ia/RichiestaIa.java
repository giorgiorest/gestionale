package it.gestionale.api.ia;

import java.util.List;
import java.util.UUID;

/**
 * Richiesta verso un {@link ProviderIa}.
 *
 * @param tenantId tenant per cui si esegue la chiamata
 * @param userId   utente autenticato che ha avviato la conversazione
 * @param messages conversazione, solo con i dati necessari al task
 * @param tools    tool che il modello può proporre di invocare
 */
public record RichiestaIa(UUID tenantId, String userId, List<MessaggioIa> messages, List<StrumentoIa> tools) {

    public RichiestaIa {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
