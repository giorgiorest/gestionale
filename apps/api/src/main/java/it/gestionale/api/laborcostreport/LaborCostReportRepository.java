package it.gestionale.api.laborcostreport;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.workentries.WorkEntryCostSnapshot;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Sola lettura, per il KPI costo manodopera (#53): aggrega gli snapshot già congelati alla
 * chiusura del rapportino ({@code work_entry_cost_snapshots}, V13), non le righe di ore grezze —
 * a differenza di {@code hoursreport}, non serve incrociare le assegnazioni: il costo è già nello
 * snapshot. Solo i rapportini chiusi hanno uno snapshot: uno ancora {@code DRAFT}/{@code
 * SUBMITTED} non compare, comportamento naturale del dato, non un filtro aggiunto.
 *
 * <p>{@code GROUP BY} per lavorazione, il livello richiesto dall'issue ("costo per lavorazione");
 * il totale di periodo è una somma più grossolana, in {@link LaborCostReportService}, di righe già
 * aggregate (ADR 0020 punto 2), non dei dati grezzi.
 */
public interface LaborCostReportRepository extends Repository<WorkEntryCostSnapshot, UUID> {

    @Query("""
            select s.workActivityId as workActivityId, sum(s.hours + s.overtimeHours) as hours,
                   sum(s.laborCost) as laborCost
            from WorkEntryCostSnapshot s, DailyReport dr
            where s.dailyReportId = dr.id
              and dr.commessaId = :commessaId and dr.tenantId = :tenantId and s.tenantId = :tenantId
              and dr.date between :from and :to and dr.deletedAt is null
            group by s.workActivityId
            """)
    List<WorkActivityLaborCostAggregate> aggregateByWorkActivity(@Param("commessaId") UUID commessaId,
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("tenantId") UUID tenantId);
}
