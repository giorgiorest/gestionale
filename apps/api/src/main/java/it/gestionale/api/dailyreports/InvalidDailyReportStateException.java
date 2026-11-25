package it.gestionale.api.dailyreports;

/**
 * Transizione di stato non valida, o scrittura su un rapportino non più {@code DRAFT} tramite un
 * endpoint ordinario (testata o righe di ore): HTTP 409, come il blocco ottimistico
 * ({@code OptimisticLockingFailureException}) — non è un errore dell'input, è un conflitto con lo
 * stato attuale del rapportino.
 */
public class InvalidDailyReportStateException extends RuntimeException {

    public InvalidDailyReportStateException(String message) {
        super(message);
    }
}
