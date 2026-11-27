package it.gestionale.api.rapportini;

/**
 * Rapportino indicato inesistente, cancellato o di un altro tenant: HTTP 400.
 */
public class RiferimentoMaterialeSconosciutoException extends RuntimeException {

    public RiferimentoMaterialeSconosciutoException(String message) {
        super(message);
    }
}
