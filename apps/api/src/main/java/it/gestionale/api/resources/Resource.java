package it.gestionale.api.resources;

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
 * cui questa persona fa parte della squadra ("capo squadra").
 *
 * <p>Stessa cancellazione logica e blocco ottimistico di {@code Customer} (ADR 0018).
 */
@Entity
@Table(name = "resources")
public class Resource {

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

    protected Resource() {
    }

    public Resource(UUID id, UUID tenantId, String firstName, String lastName, String userId, UUID teamLeadId,
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
