package it.gestionale.api.sincronizzazione;

/** Il cursore passato a {@code GET /api/sync/changes} non è decodificabile. */
public class CursoreNonValidoException extends RuntimeException {

    public CursoreNonValidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
