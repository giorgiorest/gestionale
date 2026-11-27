package it.gestionale.api.osservabilita;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurazione dell'osservabilità (log, metriche, avviso sulla salute).
 *
 * @param healthAlert avviso agli amministratori dell'installazione quando la salute dell'app cambia
 */
@ConfigurationProperties("gestionale.observability")
public record OsservabilitaProperties(@DefaultValue AllarmeSalute healthAlert) {

    /**
     * @param to       email dell'amministratore dell'installazione; vuoto = nessuna email (solo log)
     * @param interval intervallo tra due controlli della salute
     */
    public record AllarmeSalute(String to, @DefaultValue("1m") Duration interval) {
    }
}
