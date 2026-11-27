package it.gestionale.api.allegati;

/** Allegato inesistente, cancellato o di un altro tenant: HTTP 404. */
public class AllegatoNonTrovatoException extends RuntimeException {

    public AllegatoNonTrovatoException() {
        super("Allegato non trovato");
    }
}
