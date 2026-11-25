package it.gestionale.api.workentries;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Costo manodopera di una {@code WorkEntry}, congelato al momento della chiusura del rapportino
 * (GitHub #51, ADR 0019): se la tariffa dell'assegnazione cambia in seguito, questa riga non
 * cambia con essa. Scritta solo da {@code WorkEntryService.snapshotCostsForClose}, mai da un
 * upsert del client: l'id è generato in Java ({@code UUID.randomUUID()}), come
 * {@code platform_audit}, non dal client.
 *
 * <p>{@code hourlyCost}/{@code laborCost} sono {@code null} quando la risorsa non ha una tariffa
 * oraria nota in quella data (nessun {@code hourlyCostOverride} sull'assegnazione, e su
 * {@code Resource} non esiste ancora una tariffa standard di fallback): resta un costo sconosciuto,
 * non zero.
 *
 * <p>Nessuna colonna di sincronizzazione ({@code deleted_at}/{@code version}/{@code created_by}/
 * {@code updated_by}, ADR 0018): è un registro di sola lettura una volta creato, mai modificato né
 * cancellato logicamente, come {@code platform_audit}.
 */
@Entity
@Table(name = "work_entry_cost_snapshots")
public class WorkEntryCostSnapshot {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "daily_report_id", nullable = false)
    private UUID dailyReportId;

    @Column(name = "work_entry_id", nullable = false)
    private UUID workEntryId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "work_activity_id", nullable = false)
    private UUID workActivityId;

    @Column(nullable = false)
    private BigDecimal hours;

    @Column(name = "overtime_hours", nullable = false)
    private BigDecimal overtimeHours;

    @Column(name = "hourly_cost")
    private BigDecimal hourlyCost;

    @Column(name = "labor_cost")
    private BigDecimal laborCost;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WorkEntryCostSnapshot() {
    }

    public WorkEntryCostSnapshot(UUID id, UUID tenantId, UUID dailyReportId, UUID workEntryId, UUID resourceId,
                                 UUID workActivityId, BigDecimal hours, BigDecimal overtimeHours,
                                 BigDecimal hourlyCost, BigDecimal laborCost) {
        this.id = id;
        this.tenantId = tenantId;
        this.dailyReportId = dailyReportId;
        this.workEntryId = workEntryId;
        this.resourceId = resourceId;
        this.workActivityId = workActivityId;
        this.hours = hours;
        this.overtimeHours = overtimeHours;
        this.hourlyCost = hourlyCost;
        this.laborCost = laborCost;
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

    public UUID getWorkEntryId() {
        return workEntryId;
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

    public BigDecimal getHourlyCost() {
        return hourlyCost;
    }

    public BigDecimal getLaborCost() {
        return laborCost;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
