package it.gestionale.api.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param provider provider da usare ("none" = {@link ProviderIaNullo})
 * @param apiKey   chiave del provider, solo da variabile d'ambiente AI_API_KEY
 */
@ConfigurationProperties("gestionale.ai")
public record IaProperties(@DefaultValue("none") String provider, String apiKey) {

    @Override
    public String toString() {
        // Non stampare mai la chiave nei log.
        return "IaProperties[provider=" + provider + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<vuota>" : "***") + "]";
    }
}
