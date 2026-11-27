package it.gestionale.api.accesso;

import java.util.Arrays;
import java.util.List;

import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rubrica locale degli utenti del tenant, aggiornata dai claim del token a ogni accesso
 * (GET /api/me). Serve a risolvere i destinatari per ruolo e per azienda senza usare
 * le API specifiche del provider di identità.
 */
@Service
public class RubricaUtentiService {

    private final JdbcTemplate jdbc;
    private final RuoloAutoritaConverter rolesConverter;
    private final ApplicationEventPublisher events;

    public RubricaUtentiService(JdbcTemplate jdbc, RuoloAutoritaConverter rolesConverter,
                                ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.rolesConverter = rolesConverter;
        this.events = events;
    }

    /**
     * Registra o aggiorna l'utente del token. Al primo accesso gli assegna le comunicazioni
     * ancora valide che richiedono la presa visione (es. un neoassunto riceve le regole aziendali).
     */
    @Transactional
    public void sync(Jwt jwt) {
        String[] roles = rolesConverter.roles(jwt).stream().map(Ruolo::code).toArray(String[]::new);
        Boolean inserted = jdbc.queryForObject("""
                INSERT INTO app_users (tenant_id, user_id, username, name, email, roles)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, user_id) DO UPDATE
                   SET username = EXCLUDED.username, name = EXCLUDED.name, email = EXCLUDED.email,
                       roles = EXCLUDED.roles, last_seen_at = now()
                RETURNING (xmax = 0)
                """, Boolean.class, ContestoTenant.require(), jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"), roles);
        if (Boolean.TRUE.equals(inserted)) {
            // Evento sincrono, nella stessa transazione: chi ascolta (es. le notifiche) non è
            // un'ipotesi di questo modulo, che quindi non dipende da nessuno di loro.
            events.publishEvent(new UtenteRegistrato(jwt.getSubject(), Arrays.asList(roles)));
        }
    }

    @Transactional(readOnly = true)
    public List<UtenteRubrica> list() {
        return jdbc.query("""
                SELECT user_id, username, name, email, roles FROM app_users
                WHERE tenant_id = ? ORDER BY coalesce(name, username, user_id)
                """, (rs, i) -> new UtenteRubrica(rs.getString("user_id"), rs.getString("username"),
                rs.getString("name"), rs.getString("email"),
                Arrays.asList((String[]) rs.getArray("roles").getArray())), ContestoTenant.require());
    }
}
