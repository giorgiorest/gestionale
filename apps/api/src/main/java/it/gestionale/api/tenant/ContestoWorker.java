package it.gestionale.api.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ruolo di un processo di sistema che lavora per tutti i tenant (es. invio email).
 *
 * <p>Viene impostato sulla connessione come {@code app.worker} da {@link DataSourceTenant}.
 * Le tabelle tecniche che lo prevedono hanno una policy RLS dedicata
 * (es. {@code email_deliveries}: {@code app.worker = 'email-dispatcher'}); le tabelle di
 * dominio restano filtrate solo per tenant.
 */
public final class ContestoWorker {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ContestoWorker() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static <T> T callAs(String worker, Supplier<T> action) {
        String previous = CURRENT.get();
        CURRENT.set(worker);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
