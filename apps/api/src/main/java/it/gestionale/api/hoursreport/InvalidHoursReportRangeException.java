package it.gestionale.api.hoursreport;

/** L'intervallo {@code from}/{@code to} del report non è valido (es. {@code to} prima di {@code from}). */
public class InvalidHoursReportRangeException extends RuntimeException {

    public InvalidHoursReportRangeException(String message) {
        super(message);
    }
}
