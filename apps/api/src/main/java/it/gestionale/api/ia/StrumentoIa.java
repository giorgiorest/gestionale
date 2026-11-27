package it.gestionale.api.ia;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Funzione che l'assistente può invocare.
 *
 * <p>Ogni tool è un wrapper sottile su un servizio di dominio e gira nel contesto della richiesta:
 * tenant corrente (RLS) e permessi dell'utente autenticato. Non deve mai bypassarli.
 * I tool che scrivono ({@link #requiresConfirmation()} = true) vanno eseguiti solo dopo
 * la conferma esplicita dell'utente.
 */
public interface StrumentoIa {

    /** Nome univoco, esposto al modello. */
    String name();

    /** Descrizione per il modello: cosa fa e quando usarlo. */
    String description();

    /** JSON Schema degli argomenti. */
    JsonNode inputSchema();

    /** {@code true} per le operazioni di scrittura, che richiedono conferma dell'utente. */
    boolean requiresConfirmation();

    /** Esegue il tool con i permessi dell'utente corrente e restituisce il risultato in JSON. */
    JsonNode execute(JsonNode arguments);
}
