package it.gestionale.api.assegnazioni;

/** Risorsa o Commessa indicata inesistente, cancellata o di un altro tenant: HTTP 400. */
public class RiferimentoAssegnazioneSconosciutoException extends RuntimeException {

    public RiferimentoAssegnazioneSconosciutoException(String message) {
        super(message);
    }
}
