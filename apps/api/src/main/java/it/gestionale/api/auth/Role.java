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
    /** Amministratore del tenant: tutte le funzioni. È anche il ruolo di manager/proprietà dell'azienda. */
    ADMIN("admin", EnumSet.allOf(Permission.class)),
    /** Operatore: lavora sui dati e usa l'assistente. */
    OPERATOR("operator", EnumSet.of(Permission.CUSTOMERS_READ, Permission.CUSTOMERS_WRITE, Permission.ASSISTANT_USE)),
    /** Consultazione: sola lettura. */
    VIEWER("viewer", EnumSet.of(Permission.CUSTOMERS_READ)),

    // Ruoli di cantiere (docs/roadmap-alex.md): scaffolding senza permessi propri finché non esiste
    // la funzione da proteggere; ogni PR che la introduce aggiunge qui i Permission giusti.
    /** Capocantiere: verifica e conferma il lavoro delle squadre sul cantiere. */
    CAPOCANTIERE("capocantiere", EnumSet.noneOf(Permission.class)),
    /** Caposquadra: coordina la propria squadra sul cantiere. */
    CAPOSQUADRA("caposquadra", EnumSet.noneOf(Permission.class)),
    /** Operaio: lavora sul cantiere (ore, rapportini). */
    OPERAIO("operaio", EnumSet.noneOf(Permission.class)),
    /** Amministrativo: personale d'ufficio con accesso limitato (materiali, personale, attrezzature, fornitori, archivio), mai a rapportini, contabilità o pianificazione. */
    AMMINISTRATIVO("amministrativo", EnumSet.noneOf(Permission.class));

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
