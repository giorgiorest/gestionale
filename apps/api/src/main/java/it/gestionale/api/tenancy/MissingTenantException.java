package it.gestionale.api.tenancy;

/** Operazione che richiede un tenant eseguita senza tenant nel contesto. */
public class MissingTenantException extends RuntimeException {

    public MissingTenantException(String message) {
        super(message);
    }
}
