package it.gestionale.api.allegati;

/** Tipo MIME non ammesso o dimensione oltre il limite configurato (ADR 0016 punto 7): HTTP 400. */
public class AllegatoNonValidoException extends RuntimeException {

    public AllegatoNonValidoException(String message) {
        super(message);
    }
}
