package it.gestionale.api.notifiche;

import java.util.UUID;

/**
 * Notifica da creare nel tenant corrente.
 *
 * @param kind        {@code system} (generata dal gestionale) o {@code announcement} (comunicazione)
 * @param type        tipo applicativo, es. {@code announcement}, {@code customers.assigned}
 * @param link        percorso interno all'app (deve iniziare con "/"), facoltativo
 * @param requiresAck richiede la presa visione esplicita
 * @param sendEmail   invia anche un'email ai destinatari che hanno un indirizzo
 * @param senderUserId mittente (claim sub), {@code null} per il sistema
 */
public record NuovaNotifica(UUID id, String kind, String type, String title, String body, String link,
                              Pubblico audience, boolean requiresAck, boolean sendEmail,
                              String senderUserId, String senderName) {

    public static final String SYSTEM = "system";
    public static final String ANNOUNCEMENT = "announcement";

    /** Notifica di sistema, per il codice dei moduli di dominio. */
    public static NuovaNotifica system(String type, String title, String body, String link, Pubblico audience,
                                         boolean requiresAck, boolean sendEmail) {
        return new NuovaNotifica(UUID.randomUUID(), SYSTEM, type, title, body, link, audience, requiresAck,
                sendEmail, null, null);
    }
}
