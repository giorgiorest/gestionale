package it.gestionale.api.sync;

/** Il cursore passato a {@code GET /api/sync/changes} non è decodificabile. */
public class InvalidSyncCursorException extends RuntimeException {

    public InvalidSyncCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
