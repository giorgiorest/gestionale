package it.gestionale.api.riepilogoore;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cruscotto ore di una commessa nel periodo indicato (#52): le tre viste dell'issue. */
public record RiepilogoOreDto(UUID commessaId, LocalDate from, LocalDate to, List<OreSettimanaliDto> weekly,
                             List<OreRisorsaDto> byResource, List<OreLavorazioneDto> byWorkActivity) {
}
