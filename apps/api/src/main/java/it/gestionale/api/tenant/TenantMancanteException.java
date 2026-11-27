package it.gestionale.api.tenant;

/** Operazione che richiede un tenant eseguita senza tenant nel contesto. */
public class TenantMancanteException extends RuntimeException {

    public TenantMancanteException(String message) {
        super(message);
    }
}
