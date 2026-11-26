package it.gestionale.api.attachments;

/** Tipo MIME non ammesso o dimensione oltre il limite configurato (ADR 0016 punto 7): HTTP 400. */
public class InvalidAttachmentException extends RuntimeException {

    public InvalidAttachmentException(String message) {
        super(message);
    }
}
