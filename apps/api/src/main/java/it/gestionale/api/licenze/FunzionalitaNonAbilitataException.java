package it.gestionale.api.licenze;

/** La funzionalità richiesta non è inclusa nel piano del tenant (HTTP 403). */
public class FunzionalitaNonAbilitataException extends RuntimeException {

    public FunzionalitaNonAbilitataException(String feature) {
        super("Funzionalità non inclusa nel piano: " + feature);
    }
}
