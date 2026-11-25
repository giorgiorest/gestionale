package it.gestionale.api.dailyreports;

/**
 * Stato del rapportino. Salvato in minuscolo (vedi {@link DailyReportStatusConverter}).
 *
 * <p>Questa storia crea solo {@link #DRAFT}: le transizioni {@code submit}/{@code close} sono
 * un incremento successivo (endpoint dedicati, non un {@code PUT} libero sullo stato).
 */
public enum DailyReportStatus {
    DRAFT,
    SUBMITTED,
    CLOSED
}
