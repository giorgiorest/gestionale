package it.gestionale.api.licensing;

import java.util.Set;

/** Nomi delle funzionalità commerciali, condivisi con il frontend. */
public final class Features {

    public static final String AI_ASSISTANT = "aiAssistant";

    /** Tutte le funzionalità che un piano può includere (scelte nella console di piattaforma). */
    public static final Set<String> ALL = Set.of(AI_ASSISTANT);

    private Features() {
    }
}
