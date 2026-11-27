package it.gestionale.api.notifiche;

/** Notifica inesistente o non indirizzata all'utente corrente (HTTP 404). */
public class NotificaNonTrovataException extends RuntimeException {

    public NotificaNonTrovataException() {
        super("Notifica non trovata");
    }
}
