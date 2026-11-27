package it.gestionale.api.assegnazioni;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Assegnazione di una {@code Risorsa} a una {@code Commessa}, con periodo di validità e ruolo
 * contestuale a quella commessa (non il ruolo applicativo della persona: una stessa risorsa può
 * essere "capocantiere" su una commessa e "operaio" su un'altra). L'id è generato dal client
 * (UUID v7).
 *
 * <p>Stessa cancellazione logica e blocco ottimistico di {@code Cliente} (ADR 0018).
 */
@Entity
@Table(name = "resource_assignments")
public class AssegnazioneRisorsa {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "commessa_id", nullable = false)
    private UUID commessaId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(nullable = false)
    private String role;

    @Column(name = "hourly_cost_override")
    private BigDecimal hourlyCostOverride;

    @Column(nullable = false)
    private boolean active = true;

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

    protected AssegnazioneRisorsa() {
    }

    public AssegnazioneRisorsa(UUID id, UUID tenantId, UUID resourceId, UUID commessaId, LocalDate validFrom,
                              LocalDate validTo, String role, BigDecimal hourlyCostOverride, boolean active,
                              String actorUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.resourceId = resourceId;
        this.commessaId = commessaId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.role = role;
        this.hourlyCostOverride = hourlyCostOverride;
        this.active = active;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    /** Un record cancellato torna attivo: un PUT dopo la cancellazione è l'ultima volontà del client. */
    public void update(UUID resourceId, UUID commessaId, LocalDate validFrom, LocalDate validTo, String role,
                       BigDecimal hourlyCostOverride, boolean active, String actorUserId, Instant at) {
        this.resourceId = resourceId;
        this.commessaId = commessaId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.role = role;
        this.hourlyCostOverride = hourlyCostOverride;
        this.active = active;
        this.deletedAt = null;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    /** Cancellazione logica, idempotente: non fa nulla se l'assegnazione è già cancellata. */
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

    public UUID getResourceId() {
        return resourceId;
    }

    public UUID getCommessaId() {
        return commessaId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getRole() {
        return role;
    }

    public BigDecimal getHourlyCostOverride() {
        return hourlyCostOverride;
    }

    public boolean isActive() {
        return active;
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
