package it.gestionale.api.licenze;

import java.time.Instant;
import java.util.Set;

/**
 * Cosa il piano del tenant consente. È un concetto commerciale, distinto dalle feature flag tecniche.
 *
 * @param plan           nome del piano (es. "base", "pro")
 * @param maxUsers       utenti massimi
 * @param features       funzionalità incluse (es. "aiAssistant")
 * @param aiQuotaMonthly richieste mensili all'assistente AI (0 = nessuna)
 * @param validUntil     scadenza, {@code null} se illimitata
 */
public record Abilitazioni(String plan, int maxUsers, Set<String> features, int aiQuotaMonthly, Instant validUntil) {

    /** Piano vuoto, usato quando il tenant non ha una licenza valida. */
    public static final String PLAN_NONE = "none";

    public Abilitazioni {
        features = features == null ? Set.of() : Set.copyOf(features);
    }

    public static Abilitazioni none() {
        return new Abilitazioni(PLAN_NONE, 0, Set.of(), 0, null);
    }

    public boolean hasFeature(String feature) {
        return features.contains(feature);
    }
}
