package it.gestionale.api.ia;

/** Nessun provider AI disponibile (HTTP 503). */
public class IaNonDisponibileException extends RuntimeException {

    public IaNonDisponibileException(String message) {
        super(message);
    }
}
