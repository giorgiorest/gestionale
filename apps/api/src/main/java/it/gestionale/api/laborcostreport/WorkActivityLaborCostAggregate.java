package it.gestionale.api.laborcostreport;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Proiezione di {@link LaborCostReportRepository}: ore e costo manodopera già congelato (#53) di
 * una lavorazione nel periodo interrogato. {@code laborCost} è {@code null} quando nessuna riga
 * del gruppo ha un costo noto (la {@code SUM} SQL ignora i NULL: se almeno una riga è nota, il
 * totale è la somma delle note, come {@link it.gestionale.api.workentries.WorkEntryCostSnapshot}).
 */
public interface WorkActivityLaborCostAggregate {

    UUID getWorkActivityId();

    BigDecimal getHours();

    BigDecimal getLaborCost();
}
