package it.gestionale.api.commesse;

/** Cliente indicato inesistente, cancellato o di un altro tenant: HTTP 400. */
public class UnknownCustomerException extends RuntimeException {

    public UnknownCustomerException(String message) {
        super(message);
    }
}
