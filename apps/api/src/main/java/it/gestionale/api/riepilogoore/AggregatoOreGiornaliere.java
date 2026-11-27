package it.gestionale.api.riepilogoore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Proiezione di {@link RiepilogoOreRepository}: somma delle ore di un giorno/risorsa/lavorazione. */
public interface AggregatoOreGiornaliere {

    LocalDate getDate();

    UUID getResourceId();

    UUID getWorkActivityId();

    BigDecimal getHours();

    BigDecimal getOvertimeHours();
}
