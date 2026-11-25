package it.gestionale.api.workactivities;

/** Stato della lavorazione. Salvato in minuscolo (vedi {@link WorkActivityStatusConverter}). */
public enum WorkActivityStatus {
    PIANIFICATA,
    IN_CORSO,
    COMPLETATA
}
