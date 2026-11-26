package it.gestionale.api.observability;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurazione dell'osservabilità (log, metriche, avviso sulla salute).
 *
 * @param healthAlert avviso agli amministratori dell'installazione quando la salute dell'app cambia
 */
@ConfigurationProperties("gestionale.observability")
public record ObservabilityProperties(@DefaultValue HealthAlert healthAlert) {

    /**
     * @param to       email dell'amministratore dell'installazione; vuoto = nessuna email (solo log)
     * @param interval intervallo tra due controlli della salute
     */
    public record HealthAlert(String to, @DefaultValue("1m") Duration interval) {
    }
}
