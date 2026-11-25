package it.gestionale.api.resources;

/** Risorsa inesistente, cancellata o di un altro tenant: HTTP 404. */
public class UnknownResourceException extends RuntimeException {

    public UnknownResourceException(String message) {
        super(message);
    }
}
