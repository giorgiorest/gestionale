package it.gestionale.api.tenancy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Stato di accesso di un'azienda: un'azienda sospesa (o scaduta) dalla console di piattaforma
 * non può usare l'applicazione, anche con un token ancora valido.
 *
 * <p>Lo stato viene letto nel contesto del tenant (RLS) e tenuto in cache per pochi secondi;
 * la console invalida la cache quando cambia lo stato. Un tenant che non esiste nel database
 * non viene bloccato qui: senza riga non vede comunque nessun dato.
 */
@Service
public class TenantAccessService {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private record Entry(boolean blocked, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public TenantAccessService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** true se l'azienda esiste e non è attiva. Va chiamato con il tenant già nel contesto. */
    public boolean isBlocked(UUID tenantId) {
        Instant now = clock.instant();
        Entry entry = cache.get(tenantId);
        if (entry == null || entry.expiresAt().isBefore(now)) {
            List<String> status = TenantContext.callAs(tenantId,
                    () -> jdbc.queryForList("SELECT status FROM tenants WHERE id = ?", String.class, tenantId));
            boolean blocked = !status.isEmpty() && !"active".equals(status.get(0));
            entry = new Entry(blocked, now.plus(CACHE_TTL));
            cache.put(tenantId, entry);
        }
        return entry.blocked();
    }

    /** Da chiamare quando cambia lo stato dell'azienda: vale dalla richiesta successiva. */
    public void evict(UUID tenantId) {
        cache.remove(tenantId);
    }
}
