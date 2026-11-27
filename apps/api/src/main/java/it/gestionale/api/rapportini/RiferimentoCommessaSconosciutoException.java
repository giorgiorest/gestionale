package it.gestionale.api.rapportini;

/** Commessa indicata inesistente, cancellata o di un altro tenant: HTTP 400. */
public class RiferimentoCommessaSconosciutoException extends RuntimeException {

    public RiferimentoCommessaSconosciutoException(String message) {
        super(message);
    }
}
