package it.gestionale.api.licensing;

/** Licenza offline malformata, con firma non valida o scaduta. */
public class InvalidLicenseException extends RuntimeException {

    public InvalidLicenseException(String message) {
        super(message);
    }

    public InvalidLicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
