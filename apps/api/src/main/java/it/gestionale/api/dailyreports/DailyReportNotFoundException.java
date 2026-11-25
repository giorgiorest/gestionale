package it.gestionale.api.dailyreports;

/** Rapportino inesistente, cancellato o di un altro tenant: HTTP 404. */
public class DailyReportNotFoundException extends RuntimeException {

    public DailyReportNotFoundException() {
        super("Rapportino non trovato");
    }
}
