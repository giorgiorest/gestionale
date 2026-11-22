package it.gestionale.api.ai;

/** Quota mensile AI del tenant esaurita (HTTP 429). */
public class AiQuotaExceededException extends RuntimeException {

    public AiQuotaExceededException(long used, int quota) {
        super("Quota AI mensile esaurita: " + used + "/" + quota);
    }
}
