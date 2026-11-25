package it.gestionale.api.laborcostreport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KPI costo manodopera (#53): totali di periodo derivati dalla stessa aggregazione per lavorazione. */
@Service
public class LaborCostReportService {

    private final LaborCostReportRepository repository;

    public LaborCostReportService(LaborCostReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LaborCostReportDto report(UUID commessaId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidLaborCostReportRangeException(
                    "L'intervallo non è valido: \"a\" (" + to + ") è prima di \"da\" (" + from + ")");
        }
        List<WorkActivityLaborCostAggregate> rows = repository.aggregateByWorkActivity(commessaId, from, to,
                TenantContext.require());

        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalCost = null;
        for (WorkActivityLaborCostAggregate row : rows) {
            totalHours = totalHours.add(row.getHours());
            if (row.getLaborCost() != null) {
                totalCost = (totalCost == null ? BigDecimal.ZERO : totalCost).add(row.getLaborCost());
            }
        }
        List<WorkActivityLaborCostDto> byWorkActivity = rows.stream()
                .map(row -> new WorkActivityLaborCostDto(row.getWorkActivityId(), row.getHours(),
                        row.getLaborCost()))
                .toList();

        return new LaborCostReportDto(commessaId, from, to, totalHours, totalCost, byWorkActivity);
    }
}
