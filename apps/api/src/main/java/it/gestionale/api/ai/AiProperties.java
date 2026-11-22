package it.gestionale.api.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param provider provider da usare ("none" = {@link NoopAiProvider})
 * @param apiKey   chiave del provider, solo da variabile d'ambiente AI_API_KEY
 */
@ConfigurationProperties("gestionale.ai")
public record AiProperties(@DefaultValue("none") String provider, String apiKey) {

    @Override
    public String toString() {
        // Non stampare mai la chiave nei log.
        return "AiProperties[provider=" + provider + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<vuota>" : "***") + "]";
    }
}
