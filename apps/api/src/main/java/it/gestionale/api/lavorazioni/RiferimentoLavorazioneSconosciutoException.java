package it.gestionale.api.lavorazioni;

/** Commessa o lavorazione padre indicata inesistente, cancellata o di un altro tenant/commessa: HTTP 400. */
public class RiferimentoLavorazioneSconosciutoException extends RuntimeException {

    public RiferimentoLavorazioneSconosciutoException(String message) {
        super(message);
    }
}
