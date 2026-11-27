package it.gestionale.api.risorse;

/** Risorsa inesistente, cancellata o di un altro tenant: HTTP 404. */
public class RisorsaSconosciutaException extends RuntimeException {

    public RisorsaSconosciutaException(String message) {
        super(message);
    }
}
