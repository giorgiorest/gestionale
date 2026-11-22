package it.gestionale.api.ai;

/** Messaggio di una conversazione con l'assistente. */
public record AiMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}
