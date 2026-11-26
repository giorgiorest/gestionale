package it.gestionale.api.attachments;

/** Allegato inesistente, cancellato o di un altro tenant: HTTP 404. */
public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException() {
        super("Allegato non trovato");
    }
}
