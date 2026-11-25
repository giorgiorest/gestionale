package it.gestionale.api.workentries;

/**
 * Rapportino, risorsa o lavorazione indicati inesistenti, cancellati, di un altro tenant, o
 * incoerenti tra loro (lavorazione di un'altra commessa, risorsa non assegnata alla commessa in
 * quella data): HTTP 400.
 */
public class UnknownWorkEntryReferenceException extends RuntimeException {

    public UnknownWorkEntryReferenceException(String message) {
        super(message);
    }
}
