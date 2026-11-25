package it.gestionale.api.resources;

/** Capo squadra indicato inesistente, cancellato o di un altro tenant: HTTP 400. */
public class UnknownTeamLeadException extends RuntimeException {

    public UnknownTeamLeadException(String message) {
        super(message);
    }
}
