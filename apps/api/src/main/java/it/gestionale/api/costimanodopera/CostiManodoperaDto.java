package it.gestionale.api.costimanodopera;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * KPI costo manodopera di una commessa nel periodo indicato (#53): ore, costo totale e costo per
 * lavorazione. {@code totalCost} è {@code null} quando nessuna riga del periodo ha un costo noto.
 */
public record CostiManodoperaDto(UUID commessaId, LocalDate from, LocalDate to, BigDecimal hours,
                                 BigDecimal totalCost, List<CostoManodoperaLavorazioneDto> byWorkActivity) {
}
