package it.gestionale.api.risorse;

/** Capo squadra indicato inesistente, cancellato o di un altro tenant: HTTP 400. */
public class CapoSquadraSconosciutoException extends RuntimeException {

    public CapoSquadraSconosciutoException(String message) {
        super(message);
    }
}
