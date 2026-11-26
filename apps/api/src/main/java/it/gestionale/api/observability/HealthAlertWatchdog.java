package it.gestionale.api.observability;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Controlla periodicamente {@link HealthEndpoint} (lo stesso aggregato di {@code /actuator/health},
 * letto in-process: non serve esporlo via HTTP per questo) e avvisa gli amministratori
 * dell'installazione solo sulla transizione, non a ogni controllo: altrimenti un disservizio
 * prolungato manderebbe un'email al minuto.
 *
 * <p>Nessun {@code TenantContext}/{@code WorkerContext}: non legge tabelle di dominio né tecniche,
 * solo lo stato in-process dell'istanza.
 */
@Component
public class HealthAlertWatchdog {

    private final HealthEndpoint health;
    private final HealthAlertNotifier notifier;

    private volatile Status lastStatus;

    public HealthAlertWatchdog(HealthEndpoint health, HealthAlertNotifier notifier) {
        this.health = health;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${gestionale.observability.health-alert.interval:1m}",
            initialDelayString = "${gestionale.observability.health-alert.interval:1m}")
    public void run() {
        check(health.health().getStatus());
    }

    void check(Status current) {
        Status previous = lastStatus;
        lastStatus = current;
        if (previous == null || previous.equals(current)) {
            return;
        }
        if (Status.UP.equals(current)) {
            notifier.notify("Civil Beefy: salute ripristinata",
                    "Lo stato dell'applicazione è tornato " + current + " (era " + previous + ").");
        } else {
            notifier.notify("Civil Beefy: salute degradata",
                    "Lo stato dell'applicazione è " + current + " (era " + previous + "). Controlla /actuator/health.");
        }
    }
}
