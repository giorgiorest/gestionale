package it.gestionale.api.licenze;

/** Licenza offline malformata, con firma non valida o scaduta. */
public class LicenzaNonValidaException extends RuntimeException {

    public LicenzaNonValidaException(String message) {
        super(message);
    }

    public LicenzaNonValidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
