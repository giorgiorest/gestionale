package it.gestionale.api.ai;

/**
 * Fornitore di modelli linguistici, indipendente dal vendor.
 *
 * <p>Le implementazioni reali leggono la chiave da variabile d'ambiente ({@code AI_API_KEY}),
 * mai dal codice. Si seleziona con {@code gestionale.ai.provider}.
 */
public interface AiProvider {

    /** Identificativo del provider (es. "none"). */
    String name();

    /**
     * Esegue un turno di conversazione.
     *
     * @throws AiUnavailableException se il provider non è configurato o non risponde
     */
    AiResponse chat(AiRequest request);
}
