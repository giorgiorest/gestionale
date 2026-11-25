package it.gestionale.api.workactivities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Lavorazione di una commessa (es. "Armatura pilastri piano terra"), che il rapportino riuserà
 * per classificare le ore. L'id è generato dal client (UUID v7). {@code commessaId} è una chiave
 * esterna vera verso {@code commesse}. {@code parentId} prepara la gerarchia (WBS) senza ancora
 * costruire l'albero in UI: riferimento alla stessa tabella, verificato per tenant dal servizio,
 * come {@code Resource.teamLeadId}.
 *
 * <p>Stessa cancellazione logica e blocco ottimistico di {@code Customer} (ADR 0018).
 */
@Entity
@Table(name = "work_activities")
public class WorkActivity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "commessa_id", nullable = false)
    private UUID commessaId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Convert(converter = WorkActivityStatusConverter.class)
    @Column(nullable = false)
    private WorkActivityStatus status = WorkActivityStatus.PIANIFICATA;

    @Column(name = "progress_percentage", nullable = false)
    private int progressPercentage;

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

    protected WorkActivity() {
    }

    public WorkActivity(UUID id, UUID tenantId, UUID commessaId, UUID parentId, String code, String name,
                        String description, WorkActivityStatus status, int progressPercentage,
                        String actorUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.commessaId = commessaId;
        this.parentId = parentId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = status;
        this.progressPercentage = progressPercentage;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    /** Un record cancellato torna attivo: un PUT dopo la cancellazione è l'ultima volontà del client. */
    public void update(UUID commessaId, UUID parentId, String code, String name, String description,
                       WorkActivityStatus status, int progressPercentage, String actorUserId, Instant at) {
        this.commessaId = commessaId;
        this.parentId = parentId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = status;
        this.progressPercentage = progressPercentage;
        this.deletedAt = null;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    /** Cancellazione logica, idempotente: non fa nulla se la lavorazione è già cancellata. */
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

    public UUID getCommessaId() {
        return commessaId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public WorkActivityStatus getStatus() {
        return status;
    }

    public int getProgressPercentage() {
        return progressPercentage;
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
