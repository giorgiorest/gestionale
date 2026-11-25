package it.gestionale.api.laborcostreport;

/** Intervallo "da"/"a" non valido (es. "a" prima di "da"): HTTP 400. */
public class InvalidLaborCostReportRangeException extends RuntimeException {

    public InvalidLaborCostReportRangeException(String message) {
        super(message);
    }
}
