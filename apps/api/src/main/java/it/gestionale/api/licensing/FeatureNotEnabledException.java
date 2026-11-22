package it.gestionale.api.licensing;

/** La funzionalità richiesta non è inclusa nel piano del tenant (HTTP 403). */
public class FeatureNotEnabledException extends RuntimeException {

    public FeatureNotEnabledException(String feature) {
        super("Funzionalità non inclusa nel piano: " + feature);
    }
}
