package it.gestionale.api.notifications;

/**
 * Destinatari di una notifica.
 *
 * @param type  {@code user} (una persona), {@code role} (chi ha il ruolo), {@code tenant} (tutta l'azienda)
 * @param value id dell'utente (claim sub) o codice del ruolo; {@code null} per tutta l'azienda
 */
public record Audience(String type, String value) {

    public static final String USER = "user";
    public static final String ROLE = "role";
    public static final String TENANT = "tenant";

    public static Audience user(String userId) {
        return new Audience(USER, userId);
    }

    public static Audience role(String role) {
        return new Audience(ROLE, role);
    }

    public static Audience tenant() {
        return new Audience(TENANT, null);
    }
}
