package it.gestionale.api.ia;

/**
 * Fornitore di modelli linguistici, indipendente dal vendor.
 *
 * <p>Le implementazioni reali leggono la chiave da variabile d'ambiente ({@code AI_API_KEY}),
 * mai dal codice. Si seleziona con {@code gestionale.ai.provider}.
 */
public interface ProviderIa {

    /** Identificativo del provider (es. "none"). */
    String name();

    /**
     * Esegue un turno di conversazione.
     *
     * @throws IaNonDisponibileException se il provider non è configurato o non risponde
     */
    RispostaIa chat(RichiestaIa request);
}
