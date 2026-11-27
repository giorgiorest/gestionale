package it.gestionale.api.riepilogoore;

/** L'intervallo {@code from}/{@code to} del report non è valido (es. {@code to} prima di {@code from}). */
public class IntervalloRiepilogoOreNonValidoException extends RuntimeException {

    public IntervalloRiepilogoOreNonValidoException(String message) {
        super(message);
    }
}
