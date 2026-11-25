package it.gestionale.api.dailyreports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Testata del rapportino giornaliero di una commessa: un solo rapportino operativo per
 * commessa/data nell'MVP (ADR-Rapportino). L'id è generato dal client (UUID v7).
 *
 * <p>Questa storia crea/apre/salva solo lo stato {@link DailyReportStatus#DRAFT}: {@code
 * submittedAt} e {@code closedAt} sono già colonne per le transizioni di un incremento
 * successivo, ma nessun codice qui le imposta.
 *
 * <p>Stessa cancellazione logica e blocco ottimistico di {@code Customer} (ADR 0018).
 */
@Entity
@Table(name = "daily_reports")
public class DailyReport {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "commessa_id", nullable = false)
    private UUID commessaId;

    @Column(nullable = false)
    private LocalDate date;

    @Column
    private String weather;

    @Column
    private String notes;

    @Convert(converter = DailyReportStatusConverter.class)
    @Column(nullable = false)
    private DailyReportStatus status = DailyReportStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

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

    protected DailyReport() {
    }

    public DailyReport(UUID id, UUID tenantId, UUID commessaId, LocalDate date, String weather, String notes,
                       String actorUserId) {
        this.id = id;
        this.tenantId = tenantId;
        this.commessaId = commessaId;
        this.date = date;
        this.weather = weather;
        this.notes = notes;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    /**
     * Un record cancellato torna attivo: un PUT dopo la cancellazione è l'ultima volontà del
     * client. Non tocca lo stato: questa storia non lo cambia mai da quello di creazione.
     */
    public void update(UUID commessaId, LocalDate date, String weather, String notes, String actorUserId,
                       Instant at) {
        this.commessaId = commessaId;
        this.date = date;
        this.weather = weather;
        this.notes = notes;
        this.deletedAt = null;
        this.updatedBy = actorUserId;
        this.updatedAt = at;
    }

    /** Cancellazione logica, idempotente: non fa nulla se il rapportino è già cancellato. */
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

    public LocalDate getDate() {
        return date;
    }

    public String getWeather() {
        return weather;
    }

    public String getNotes() {
        return notes;
    }

    public DailyReportStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
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
