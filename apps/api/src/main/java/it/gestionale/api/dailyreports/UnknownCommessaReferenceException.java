package it.gestionale.api.dailyreports;

/** Commessa indicata inesistente, cancellata o di un altro tenant: HTTP 400. */
public class UnknownCommessaReferenceException extends RuntimeException {

    public UnknownCommessaReferenceException(String message) {
        super(message);
    }
}
