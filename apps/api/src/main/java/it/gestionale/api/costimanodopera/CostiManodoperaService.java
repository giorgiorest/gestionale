package it.gestionale.api.costimanodopera;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KPI costo manodopera (#53): totali di periodo derivati dalla stessa aggregazione per lavorazione. */
@Service
public class CostiManodoperaService {

    private final CostiManodoperaRepository repository;

    public CostiManodoperaService(CostiManodoperaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CostiManodoperaDto report(UUID commessaId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IntervalloCostiManodoperaNonValidoException(
                    "L'intervallo non è valido: \"a\" (" + to + ") è prima di \"da\" (" + from + ")");
        }
        List<AggregatoCostoManodoperaLavorazione> rows = repository.aggregateByWorkActivity(commessaId, from, to,
                ContestoTenant.require());

        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalCost = null;
        for (AggregatoCostoManodoperaLavorazione row : rows) {
            totalHours = totalHours.add(row.getHours());
            if (row.getLaborCost() != null) {
                totalCost = (totalCost == null ? BigDecimal.ZERO : totalCost).add(row.getLaborCost());
            }
        }
        List<CostoManodoperaLavorazioneDto> byWorkActivity = rows.stream()
                .map(row -> new CostoManodoperaLavorazioneDto(row.getWorkActivityId(), row.getHours(),
                        row.getLaborCost()))
                .toList();

        return new CostiManodoperaDto(commessaId, from, to, totalHours, totalCost, byWorkActivity);
    }
}
