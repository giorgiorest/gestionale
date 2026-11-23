package it.gestionale.api.notifications;

/** Destinatario non valido (utente mai entrato nel tenant o ruolo inesistente): HTTP 400. */
public class UnknownRecipientException extends RuntimeException {

    public UnknownRecipientException(String message) {
        super(message);
    }
}
