package it.gestionale.api.auth;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Ruoli applicativi (profili utente), assegnati nel provider di identità (Keycloak, realm role).
 * Ogni ruolo concede un insieme di {@link Permission}: la mappatura sta qui, nel backend,
 * così cambiare cosa può fare un ruolo non richiede di toccare Keycloak.
 */
public enum Role {
    /** Amministratore del tenant: tutte le funzioni. */
    ADMIN("admin", EnumSet.allOf(Permission.class)),
    /** Operatore: lavora sui dati e usa l'assistente. */
    OPERATOR("operator", EnumSet.of(Permission.CUSTOMERS_READ, Permission.CUSTOMERS_WRITE, Permission.ASSISTANT_USE)),
    /** Consultazione: sola lettura. */
    VIEWER("viewer", EnumSet.of(Permission.CUSTOMERS_READ));

    private final String code;
    private final Set<Permission> permissions;

    Role(String code, Set<Permission> permissions) {
        this.code = code;
        this.permissions = permissions;
    }

    public String code() {
        return code;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** Ruolo con il codice indicato; i ruoli sconosciuti (es. quelli tecnici di Keycloak) sono ignorati. */
    public static Optional<Role> fromCode(String code) {
        for (Role role : values()) {
            if (role.code.equals(code)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
