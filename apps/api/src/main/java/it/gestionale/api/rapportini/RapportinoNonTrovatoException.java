package it.gestionale.api.rapportini;

/** Rapportino inesistente, cancellato o di un altro tenant: HTTP 404. */
public class RapportinoNonTrovatoException extends RuntimeException {

    public RapportinoNonTrovatoException() {
        super("Rapportino non trovato");
    }
}
