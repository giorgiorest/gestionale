package it.gestionale.api.resourceassignments;

/** Resource o Commessa indicata inesistente, cancellata o di un altro tenant: HTTP 400. */
public class UnknownAssignmentReferenceException extends RuntimeException {

    public UnknownAssignmentReferenceException(String message) {
        super(message);
    }
}
