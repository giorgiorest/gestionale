package it.gestionale.api.ia;

/** Quota mensile AI del tenant esaurita (HTTP 429). */
public class QuotaIaSuperataException extends RuntimeException {

    public QuotaIaSuperataException(long used, int quota) {
        super("Quota AI mensile esaurita: " + used + "/" + quota);
    }
}
