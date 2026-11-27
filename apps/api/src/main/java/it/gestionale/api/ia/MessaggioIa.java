package it.gestionale.api.ia;

/** Messaggio di una conversazione con l'assistente. */
public record MessaggioIa(Ruolo role, String content) {

    public enum Ruolo {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}
