package it.gestionale.api.dailyreportmaterials;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Riga di materiale impiegato in un rapportino giornaliero ({@code DailyReport}, #49): nessun
 * catalogo materiali esiste ancora (GitHub #55, {@code docs/roadmap.md} C.1), quindi
 * {@code description} e {@code unit} sono testo libero, non un riferimento a un'anagrafica — da
 * rivedere quando quella storia arriva (trigger di revisione).
 *
 * <p>Stessa cancellazione logica, blocco ottimistico e blocco in base allo stato del rapportino
 * (vedi {@code DailyReportMaterialService.upsert}/{@code delete}) di {@code WorkEntry}.
 */
@Entity
@Table(name = "daily_report_materials")
public class DailyReportMaterial {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "daily_report_id", nullable = false)
    private UUID dailyReportId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String unit;

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

    protected DailyReportMaterial() {
    }

    public DailyReportMaterial(UUID id, UUID tenantId, UUID dailyReportId, String description, BigDecimal quantity,
                               String unit, String notes, String actorUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.dailyReportId = dailyReportId;
        this.description = description;
        this.quantity = quantity;
        this.unit = unit;
        this.notes = notes;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    /** Un record cancellato torna attivo: un PUT dopo la cancellazione è l'ultima volontà del client. */
    public void update(UUID dailyReportId, String description, BigDecimal quantity, String unit, String notes,
                       String actorUserId, Instant at) {
        this.dailyReportId = dailyReportId;
        this.description = description;
        this.quantity = quantity;
        this.unit = unit;
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

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
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
