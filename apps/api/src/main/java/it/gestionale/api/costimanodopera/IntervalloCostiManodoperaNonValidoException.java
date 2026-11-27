package it.gestionale.api.costimanodopera;

/** Intervallo "da"/"a" non valido (es. "a" prima di "da"): HTTP 400. */
public class IntervalloCostiManodoperaNonValidoException extends RuntimeException {

    public IntervalloCostiManodoperaNonValidoException(String message) {
        super(message);
    }
}
