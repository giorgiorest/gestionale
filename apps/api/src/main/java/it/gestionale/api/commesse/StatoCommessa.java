package it.gestionale.api.commesse;

/** Stato della commessa. Salvato in minuscolo (vedi {@link StatoCommessaConverter}). */
public enum StatoCommessa {
    PIANIFICATA,
    IN_CORSO,
    SOSPESA,
    CONCLUSA
}
