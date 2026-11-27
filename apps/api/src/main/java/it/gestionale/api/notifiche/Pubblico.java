package it.gestionale.api.notifiche;

/**
 * Destinatari di una notifica.
 *
 * @param type  {@code user} (una persona), {@code role} (chi ha il ruolo), {@code tenant} (tutta l'azienda)
 * @param value id dell'utente (claim sub) o codice del ruolo; {@code null} per tutta l'azienda
 */
public record Pubblico(String type, String value) {

    public static final String USER = "user";
    public static final String ROLE = "role";
    public static final String TENANT = "tenant";

    public static Pubblico user(String userId) {
        return new Pubblico(USER, userId);
    }

    public static Pubblico role(String role) {
        return new Pubblico(ROLE, role);
    }

    public static Pubblico tenant() {
        return new Pubblico(TENANT, null);
    }
}
