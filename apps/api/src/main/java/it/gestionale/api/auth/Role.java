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
    /**
     * Operatore: lavora sui dati e usa l'assistente. {@code commesse:read} e {@code resources:read}
     * sono qui solo perché senza vedere le commesse e le risorse esistenti, il permesso su
     * {@code resource-assignments} sarebbe irraggiungibile dalla UI (tab "Risorse" della commessa):
     * non è la pagina delle risorse o delle commesse a essere concessa, solo la loro lettura.
     */
    OPERATOR("operator", EnumSet.of(Permission.CUSTOMERS_READ, Permission.CUSTOMERS_WRITE, Permission.ASSISTANT_USE,
            Permission.RESOURCES_READ, Permission.COMMESSE_READ, Permission.RESOURCE_ASSIGNMENTS_READ,
            Permission.RESOURCE_ASSIGNMENTS_WRITE, Permission.WORK_ACTIVITIES_READ,
            Permission.WORK_ACTIVITIES_WRITE)),
    /** Consultazione: sola lettura. {@code resources:read}/{@code commesse:read} per lo stesso motivo di {@code OPERATOR}. */
    VIEWER("viewer", EnumSet.of(Permission.CUSTOMERS_READ, Permission.RESOURCES_READ, Permission.COMMESSE_READ,
            Permission.RESOURCE_ASSIGNMENTS_READ, Permission.WORK_ACTIVITIES_READ)),

    // Ruoli di cantiere (docs/roadmap-alex.md): ogni PR che introduce una funzione di cantiere
    // aggiunge qui i Permission giusti ai ruoli giusti.
    /**
     * Capocantiere: verifica e conferma il lavoro delle squadre sul cantiere, gestisce il
     * personale e la propria commessa (anche lo stato: è lui a saperla in corso o conclusa).
     */
    CAPOCANTIERE("capocantiere", EnumSet.of(Permission.RESOURCES_READ, Permission.RESOURCES_WRITE,
            Permission.COMMESSE_READ, Permission.COMMESSE_WRITE, Permission.RESOURCE_ASSIGNMENTS_READ,
            Permission.RESOURCE_ASSIGNMENTS_WRITE, Permission.WORK_ACTIVITIES_READ,
            Permission.WORK_ACTIVITIES_WRITE)),
    /** Caposquadra: coordina la propria squadra sul cantiere. */
    CAPOSQUADRA("caposquadra", EnumSet.of(Permission.RESOURCES_READ, Permission.COMMESSE_READ,
            Permission.RESOURCE_ASSIGNMENTS_READ, Permission.WORK_ACTIVITIES_READ)),
    /** Operaio: lavora sul cantiere (ore, rapportini). */
    OPERAIO("operaio", EnumSet.of(Permission.RESOURCES_READ, Permission.COMMESSE_READ,
            Permission.RESOURCE_ASSIGNMENTS_READ, Permission.WORK_ACTIVITIES_READ)),
    /** Amministrativo: personale d'ufficio con accesso limitato (materiali, personale, attrezzature, fornitori, archivio), mai a rapportini, contabilità o pianificazione. */
    AMMINISTRATIVO("amministrativo", EnumSet.of(Permission.RESOURCES_READ, Permission.RESOURCES_WRITE,
            Permission.COMMESSE_READ, Permission.COMMESSE_WRITE));

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
