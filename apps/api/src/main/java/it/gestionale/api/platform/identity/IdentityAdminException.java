package it.gestionale.api.platform.identity;

/** Errore del provider di identità. {@link #kind} decide la risposta HTTP. */
public class IdentityAdminException extends RuntimeException {

    public enum Kind {
        /** Utente inesistente o di un'altra azienda. */
        NOT_FOUND,
        /** Nome utente o email già in uso. */
        CONFLICT,
        /** Provider non raggiungibile o non configurato. */
        UNAVAILABLE
    }

    private final Kind kind;

    public IdentityAdminException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public IdentityAdminException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
