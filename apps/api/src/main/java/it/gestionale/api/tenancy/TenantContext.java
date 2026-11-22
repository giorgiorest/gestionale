package it.gestionale.api.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Tenant corrente del thread che sta servendo la richiesta.
 *
 * <p>Usa un {@link ThreadLocal}: {@code ScopedValue} in Java 21 è ancora in preview.
 * Il valore va sempre rimosso a fine richiesta (lo fa {@link TenantFilter}); per il codice
 * fuori dal ciclo HTTP (job, seeder, test) usare {@link #runAs} / {@link #callAs}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        return current().orElseThrow(() -> new MissingTenantException("Nessun tenant nel contesto corrente"));
    }

    static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Esegue {@code action} con il tenant indicato, ripristinando poi il valore precedente. */
    public static void runAs(UUID tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /** Come {@link #runAs}, con valore di ritorno. */
    public static <T> T callAs(UUID tenantId, Callable<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
