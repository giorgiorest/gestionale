package it.gestionale.api.piattaforma;

import org.springframework.http.HttpStatus;

/** Errore della console di piattaforma con il suo stato HTTP. */
public class PiattaformaException extends RuntimeException {

    private final HttpStatus status;

    public PiattaformaException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static PiattaformaException tenantNotFound() {
        return new PiattaformaException(HttpStatus.NOT_FOUND, "Azienda non trovata");
    }
}
