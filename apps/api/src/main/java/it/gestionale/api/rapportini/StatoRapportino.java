package it.gestionale.api.rapportini;

/**
 * Stato del rapportino. Salvato in minuscolo (vedi {@link StatoRapportinoConverter}).
 *
 * <p>Questa storia crea solo {@link #DRAFT}: le transizioni {@code submit}/{@code close} sono
 * un incremento successivo (endpoint dedicati, non un {@code PUT} libero sullo stato).
 */
public enum StatoRapportino {
    DRAFT,
    SUBMITTED,
    CLOSED
}
