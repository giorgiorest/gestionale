package it.gestionale.api.workactivities;

/** Commessa o lavorazione padre indicata inesistente, cancellata o di un altro tenant/commessa: HTTP 400. */
public class UnknownWorkActivityReferenceException extends RuntimeException {

    public UnknownWorkActivityReferenceException(String message) {
        super(message);
    }
}
