package it.gestionale.api.workentries;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Riga di ore di un rapportino giornaliero ({@code DailyReport}, #49): una persona
 * ({@code Resource}) che ha lavorato a una lavorazione ({@code WorkActivity}) della stessa
 * commessa del rapportino. L'id è generato dal client (UUID v7).
 *
 * <p>Il totale ore della UI è derivato da queste righe, non un campo salvato. Lo snapshot del
 * costo manodopera alla chiusura del rapportino è un incremento successivo (#51), non qui.
 *
 * <p>Stessa cancellazione logica e blocco ottimistico di {@code Customer} (ADR 0018).
 */
@Entity
@Table(name = "work_entries")
public class WorkEntry {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "daily_report_id", nullable = false)
    private UUID dailyReportId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "work_activity_id", nullable = false)
    private UUID workActivityId;

    @Column(nullable = false)
    private BigDecimal hours;

    @Column(name = "overtime_hours", nullable = false)
    private BigDecimal overtimeHours;

    @Column
    private String notes;

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

    protected WorkEntry() {
    }

    public WorkEntry(UUID id, UUID tenantId, UUID dailyReportId, UUID resourceId, UUID workActivityId,
                     BigDecimal hours, BigDecimal overtimeHours, String notes, String actorUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.dailyReportId = dailyReportId;
        this.resourceId = resourceId;
        this.workActivityId = workActivityId;
        this.hours = hours;
        this.overtimeHours = overtimeHours;
        this.notes = notes;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    /** Un record cancellato torna attivo: un PUT dopo la cancellazione è l'ultima volontà del client. */
    public void update(UUID dailyReportId, UUID resourceId, UUID workActivityId, BigDecimal hours,
                       BigDecimal overtimeHours, String notes, String actorUserId, Instant at) {
        this.dailyReportId = dailyReportId;
        this.resourceId = resourceId;
        this.workActivityId = workActivityId;
        this.hours = hours;
        this.overtimeHours = overtimeHours;
        this.notes = notes;
        this.deletedAt = null;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    /** Cancellazione logica, idempotente: non fa nulla se la riga è già cancellata. */
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

    public UUID getDailyReportId() {
        return dailyReportId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public UUID getWorkActivityId() {
        return workActivityId;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }

    public String getNotes() {
        return notes;
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
