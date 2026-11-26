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
     * {@code labor-cost:read/write} (#53): ha già {@code resource-assignments:write}, cioè può già
     * impostare un costo (l'override contestuale alla commessa) — la tariffa oraria base della
     * risorsa è la stessa responsabilità, non un dato nuovo da proteggere diversamente.
     */
    OPERATOR("operator", EnumSet.of(Permission.CUSTOMERS_READ, Permission.CUSTOMERS_WRITE,
            Permission.SUPPLIERS_READ, Permission.SUPPLIERS_WRITE, Permission.ASSISTANT_USE,
            Permission.RESOURCES_READ, Permission.COMMESSE_READ, Permission.RESOURCE_ASSIGNMENTS_READ,
            Permission.RESOURCE_ASSIGNMENTS_WRITE, Permission.WORK_ACTIVITIES_READ,
            Permission.WORK_ACTIVITIES_WRITE, Permission.DAILY_REPORTS_READ, Permission.DAILY_REPORTS_WRITE,
            Permission.WORK_ENTRIES_READ, Permission.WORK_ENTRIES_WRITE, Permission.DAILY_REPORT_MATERIALS_READ,
            Permission.DAILY_REPORT_MATERIALS_WRITE, Permission.LABOR_COST_READ,
            Permission.LABOR_COST_WRITE, Permission.ATTACHMENTS_READ, Permission.ATTACHMENTS_WRITE)),
    /**
     * Consultazione: sola lettura. {@code resources:read}/{@code commesse:read} per lo stesso
     * motivo di {@code OPERATOR}. {@code labor-cost:read} (#53, non {@code write}, coerente con un
     * ruolo di sola lettura): ha già {@code resource-assignments:read}, cioè vede già l'override di
     * costo contestuale — stesso trattamento per la tariffa base.
     */
    VIEWER("viewer", EnumSet.of(Permission.CUSTOMERS_READ, Permission.SUPPLIERS_READ, Permission.RESOURCES_READ,
            Permission.COMMESSE_READ, Permission.RESOURCE_ASSIGNMENTS_READ, Permission.WORK_ACTIVITIES_READ,
            Permission.DAILY_REPORTS_READ, Permission.WORK_ENTRIES_READ, Permission.DAILY_REPORT_MATERIALS_READ,
            Permission.LABOR_COST_READ, Permission.ATTACHMENTS_READ)),

    // Ruoli di cantiere (docs/roadmap.md, Fasi A-I): ogni PR che introduce una funzione di cantiere
    // aggiunge qui i Permission giusti ai ruoli giusti.
    /**
     * Capocantiere: verifica e conferma il lavoro delle squadre sul cantiere, gestisce il
     * personale e la propria commessa (anche lo stato: è lui a saperla in corso o conclusa).
     * {@code labor-cost:read/write} (#53): gestisce già il personale ({@code resources:write}) e
     * già imposta costi (l'override contestuale su {@code resource-assignments:write}) — la
     * tariffa base è coerente con quella stessa responsabilità. {@code attachments:read/write}
     * (roadmap 2.4): già scrive il rapportino ({@code daily-reports:write}), e le foto di
     * cantiere sono proprio il caso d'uso che ha motivato gli allegati (ADR 0016).
     */
    CAPOCANTIERE("capocantiere", EnumSet.of(Permission.RESOURCES_READ, Permission.RESOURCES_WRITE,
            Permission.COMMESSE_READ, Permission.COMMESSE_WRITE, Permission.RESOURCE_ASSIGNMENTS_READ,
            Permission.RESOURCE_ASSIGNMENTS_WRITE, Permission.WORK_ACTIVITIES_READ,
            Permission.WORK_ACTIVITIES_WRITE, Permission.DAILY_REPORTS_READ, Permission.DAILY_REPORTS_WRITE,
            Permission.WORK_ENTRIES_READ, Permission.WORK_ENTRIES_WRITE, Permission.DAILY_REPORT_MATERIALS_READ,
            Permission.DAILY_REPORT_MATERIALS_WRITE, Permission.LABOR_COST_READ,
            Permission.LABOR_COST_WRITE, Permission.ATTACHMENTS_READ, Permission.ATTACHMENTS_WRITE)),
    /**
     * Caposquadra: coordina la propria squadra sul cantiere. Il piano prevede scrittura sul
     * rapportino (e sulle sue righe, di ore o di materiale) limitata alla propria squadra
     * ("RW squadra"): finché quel controllo non esiste (richiede incrociare
     * {@code daily-reports}/{@code work-entries}/{@code daily-report-materials} con le
     * assegnazioni della squadra, {@code resource-assignments}), solo lettura qui, non il
     * pieno {@code DAILY_REPORTS_WRITE}/{@code WORK_ENTRIES_WRITE} di {@code CAPOCANTIERE} —
     * altrimenti scriverebbe anche sui rapportini di commesse/squadre non sue. Niente
     * {@code labor-cost:read} (#53, a differenza di {@code CAPOCANTIERE}): coordina la squadra sul
     * campo, non ne gestisce il personale a livello economico — il costo orario è una
     * retribuzione implicita, non un dato di coordinamento. {@code attachments:read} (roadmap
     * 2.4, non anche {@code write}, coerente con la sola lettura che ha sui rapportini): vede le
     * foto già caricate dal capocantiere, non ne carica di proprie finché non scrive anche il
     * rapportino.
     */
    CAPOSQUADRA("caposquadra", EnumSet.of(Permission.RESOURCES_READ, Permission.COMMESSE_READ,
            Permission.RESOURCE_ASSIGNMENTS_READ, Permission.WORK_ACTIVITIES_READ,
            Permission.DAILY_REPORTS_READ, Permission.WORK_ENTRIES_READ,
            Permission.DAILY_REPORT_MATERIALS_READ, Permission.ATTACHMENTS_READ)),
    /**
     * Operaio: lavora sul cantiere (ore, rapportini). Non ancora la testata del rapportino né le
     * sue righe di ore (#50): il piano le marca "proprie righe", cioè scrittura delle {@code
     * work-entries} limitata alle righe della risorsa collegata all'utente stesso — una scelta di
     * prodotto non ancora presa (richiederebbe incrociare {@code Resource.userId} con {@code
     * WorkEntry.resourceId}, un controllo diverso da quello di {@code CAPOSQUADRA} sopra). Niente
     * permesso finché quella scelta non arriva, per non concedere nel frattempo una scrittura più
     * ampia di quella voluta. Niente {@code labor-cost:read} (#53): è l'ultima persona che deve
     * vedere il proprio costo orario (o quello dei colleghi) — una retribuzione implicita.
     */
    OPERAIO("operaio", EnumSet.of(Permission.RESOURCES_READ, Permission.COMMESSE_READ,
            Permission.RESOURCE_ASSIGNMENTS_READ, Permission.WORK_ACTIVITIES_READ)),
    /**
     * Amministrativo: personale d'ufficio con accesso limitato (materiali, personale, attrezzature,
     * fornitori, archivio), mai a rapportini, contabilità o pianificazione. Niente
     * {@code labor-cost:read/write} (#53), nonostante {@code resources:write}: il costo orario è
     * un dato di contabilità/payroll, esattamente ciò che questo ruolo non deve mai toccare. Niente
     * {@code daily-report-materials:read/write} per lo stesso motivo di {@code daily-reports}: i
     * "materiali" di questo commento sono il futuro catalogo/magazzino di cantiere (GitHub #55,
     * {@code docs/roadmap.md} C.1), non le righe di materiale del rapportino, che restano
     * "mai a rapportini". {@code suppliers:read/write} (roadmap 2.2): i fornitori sono
     * esplicitamente tra le sue aree di accesso. {@code attachments:read/write} (roadmap 2.4):
     * "archivio" è altrettanto esplicito tra le sue aree.
     */
    AMMINISTRATIVO("amministrativo", EnumSet.of(Permission.RESOURCES_READ, Permission.RESOURCES_WRITE,
            Permission.COMMESSE_READ, Permission.COMMESSE_WRITE, Permission.SUPPLIERS_READ,
            Permission.SUPPLIERS_WRITE, Permission.ATTACHMENTS_READ, Permission.ATTACHMENTS_WRITE));

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
