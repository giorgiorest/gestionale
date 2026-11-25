package it.gestionale.api.commesse;

/** Stato della commessa. Salvato in minuscolo (vedi {@link CommessaStatoConverter}). */
public enum CommessaStato {
    PIANIFICATA,
    IN_CORSO,
    SOSPESA,
    CONCLUSA
}
