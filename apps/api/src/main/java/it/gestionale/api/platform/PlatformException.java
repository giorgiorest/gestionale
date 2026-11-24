package it.gestionale.api.platform;

import org.springframework.http.HttpStatus;

/** Errore della console di piattaforma con il suo stato HTTP. */
public class PlatformException extends RuntimeException {

    private final HttpStatus status;

    public PlatformException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static PlatformException tenantNotFound() {
        return new PlatformException(HttpStatus.NOT_FOUND, "Azienda non trovata");
    }
}
