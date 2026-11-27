package it.gestionale.api.lavorazioni;

/** Stato della lavorazione. Salvato in minuscolo (vedi {@link StatoLavorazioneConverter}). */
public enum StatoLavorazione {
    PIANIFICATA,
    IN_CORSO,
    COMPLETATA
}
