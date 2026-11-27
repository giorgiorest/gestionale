package it.gestionale.api.notifiche;

/** Destinatario non valido (utente mai entrato nel tenant o ruolo inesistente): HTTP 400. */
public class DestinatarioSconosciutoException extends RuntimeException {

    public DestinatarioSconosciutoException(String message) {
        super(message);
    }
}
