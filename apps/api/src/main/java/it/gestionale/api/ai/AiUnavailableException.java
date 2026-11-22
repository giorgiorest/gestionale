package it.gestionale.api.ai;

/** Nessun provider AI disponibile (HTTP 503). */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
