package it.gestionale.api.risorse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Risorsa del tenant (dipendente che compila i rapportini). L'id è generato dal client (UUID v7).
 *
 * <p>{@code userId} collega la risorsa alla rubrica {@code app_users} (claim {@code sub}), ma non
 * è una chiave esterna: {@code app_users} si popola solo al primo accesso, quindi una risorsa può
 * esistere prima che la persona abbia mai effettuato il login. {@code teamLeadId} è la risorsa di
 * cui questa persona fa parte della squadra ("capo squadra"); le squadre sono a un solo livello
 * (verificato in {@code RisorsaService}): un capo squadra non può a sua volta avere un capo squadra.
 *
 * <p>Stessa cancellazione logica e blocco ottimistico di {@code Cliente} (ADR 0018).
 */
@Entity
@Table(name = "resources")
public class Risorsa {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "team_lead_id")
    private UUID teamLeadId;

    /**
     * Tariffa oraria base (V14, #53): fallback usato nello snapshot del costo manodopera quando
     * l'assegnazione attiva non ha un {@code hourlyCostOverride}. Non esposta da {@code
     * RisorsaDto}/{@code RisorsaController} (permesso {@code resources:read/write}, troppo
     * largo per un dato di retribuzione implicita): solo da {@code CostoManodoperaRisorsaDto} e dai due
     * endpoint dedicati, dietro {@code labor-cost:read/write}.
     */
    @Column(name = "standard_hourly_cost")
    private BigDecimal standardHourlyCost;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Risorsa() {
    }

    public Risorsa(UUID id, UUID tenantId, String firstName, String lastName, String userId, UUID teamLeadId,
                    String actorUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.userId = userId;
        this.teamLeadId = teamLeadId;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    /** Un record cancellato torna attivo: un PUT dopo la cancellazione è l'ultima volontà del client. */
    public void update(String firstName, String lastName, String userId, UUID teamLeadId, String actorUserId,
                       Instant at) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.userId = userId;
        this.teamLeadId = teamLeadId;
        this.deletedAt = null;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    /** Imposta la tariffa oraria base (#53), indipendentemente dagli altri campi. */
    public void updateStandardHourlyCost(BigDecimal standardHourlyCost, String actorUserId, Instant at) {
        this.standardHourlyCost = standardHourlyCost;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    /** Cancellazione logica, idempotente: non fa nulla se la risorsa è già cancellata. */
    public void markDeleted(String actorUserId, Instant at) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = at;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUserId() {
        return userId;
    }

    public UUID getTeamLeadId() {
        return teamLeadId;
    }

    public BigDecimal getStandardHourlyCost() {
        return standardHourlyCost;
    }

    public long getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
