package it.gestionale.api.notifiche;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurazione delle notifiche.
 *
 * @param email invio delle notifiche via email
 * @param missingDailyReport controllo periodico del rapportino mancante (#54)
 */
@ConfigurationProperties("gestionale.notifications")
public record NotificheProperties(@DefaultValue Email email, @DefaultValue RapportinoMancante missingDailyReport) {

    /**
     * @param enabled     se false le email non vengono accodate
     * @param from        mittente (es. "Civil Beefy &lt;noreply@azienda.it&gt;")
     * @param appUrl      indirizzo pubblico della web app, per il link nell'email
     * @param includeBody se false l'email contiene solo titolo e link (per comunicazioni riservate)
     * @param pollInterval intervallo di controllo della coda
     * @param maxAttempts tentativi prima di segnare l'invio come fallito
     */
    public record Email(@DefaultValue("false") boolean enabled,
                        @DefaultValue("Civil Beefy <noreply@localhost>") String from,
                        String appUrl,
                        @DefaultValue("true") boolean includeBody,
                        @DefaultValue("30s") Duration pollInterval,
                        @DefaultValue("5") int maxAttempts) {
    }

    /** @param cron quando controllare le commesse in corso senza rapportino (default: ogni giorno alle 19) */
    public record RapportinoMancante(@DefaultValue("0 0 19 * * *") String cron) {
    }
}
