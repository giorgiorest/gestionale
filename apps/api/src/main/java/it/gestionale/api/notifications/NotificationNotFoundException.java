package it.gestionale.api.notifications;

/** Notifica inesistente o non indirizzata all'utente corrente (HTTP 404). */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Notifica non trovata");
    }
}
