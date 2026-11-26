package it.gestionale.api.dailyreportmaterials;

/**
 * Rapportino indicato inesistente, cancellato o di un altro tenant: HTTP 400.
 */
public class UnknownDailyReportMaterialReferenceException extends RuntimeException {

    public UnknownDailyReportMaterialReferenceException(String message) {
        super(message);
    }
}
