package it.gestionale.api.notifiche;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.gestionale.api.accesso.Ruolo;
import it.gestionale.api.accesso.UtenteRegistrato;
import it.gestionale.api.notifiche.NotificaDtos.Counts;
import it.gestionale.api.notifiche.NotificaDtos.NotificaUtente;
import it.gestionale.api.notifiche.NotificaDtos.RiepilogoComunicazione;
import it.gestionale.api.notifiche.NotificaDtos.StatoDestinatario;
import it.gestionale.api.tenant.ContestoTenant;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifiche e comunicazioni ai dipendenti del tenant corrente.
 *
 * <ul>
 *   <li>Alla creazione i destinatari vengono risolti dalla rubrica ({@code app_users}):
 *       una riga in {@code notification_recipients} per persona.</li>
 *   <li>Lo stato (letta, presa visione) è per destinatario; la presa visione registra l'ora del server.</li>
 *   <li>Le email vengono accodate in {@code email_deliveries} e inviate da {@link InviatoreEmail}.</li>
 * </ul>
 * Tutte le query girano nel tenant corrente (RLS) e filtrano esplicitamente per tenant e utente.
 */
@Service
public class NotificaService {

    private static final String MY_COLUMNS = """
            n.id, n.kind, n.type, n.title, n.body, n.link, n.sender_name, n.created_at, n.requires_ack,
            r.read_at, r.acknowledged_at
            """;

    private final JdbcTemplate jdbc;
    private final NotificheProperties properties;
    private final Clock clock;

    public NotificaService(JdbcTemplate jdbc, NotificheProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ creazione

    /**
     * Crea la notifica e la assegna ai destinatari. Idempotente sull'id: se esiste già nel
     * tenant non fa nulla (le comunicazioni arrivano dalla outbox del client e possono essere reinviate).
     *
     * @return numero di destinatari
     */
    @Transactional
    public int send(NuovaNotifica n) {
        UUID tenantId = ContestoTenant.require();
        validate(n);

        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE tenant_id = ? AND id = ?", Integer.class, tenantId, n.id());
        if (existing != null && existing > 0) {
            return recipientsCount(n.id());
        }

        jdbc.update("""
                INSERT INTO notifications (id, tenant_id, kind, type, title, body, link, audience_type, audience_value,
                                           requires_ack, send_email, sender_user_id, sender_name, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, n.id(), tenantId, n.kind(), n.type(), n.title(), n.body() == null ? "" : n.body(), n.link(),
                n.audience().type(), n.audience().value(), n.requiresAck(), n.sendEmail(), n.senderUserId(),
                n.senderName(), Timestamp.from(clock.instant()));

        List<String> recipients = jdbc.queryForList("""
                INSERT INTO notification_recipients (notification_id, tenant_id, user_id)
                SELECT ?, tenant_id, user_id FROM app_users
                WHERE tenant_id = ?
                  AND (? = 'tenant'
                       OR (? = 'role' AND ? = ANY (roles))
                       OR (? = 'user' AND user_id = ?))
                RETURNING user_id
                """, String.class, n.id(), tenantId, n.audience().type(), n.audience().type(), n.audience().value(),
                n.audience().type(), n.audience().value());

        if (n.sendEmail()) {
            queueEmails(n.id(), recipients);
        }
        return recipients.size();
    }

    /**
     * Assegna a un utente appena registrato nella rubrica le notifiche che richiedono la presa
     * visione ed erano indirizzate a tutta l'azienda o a uno dei suoi ruoli. Si attiva da sola
     * all'evento {@link UtenteRegistrato} del modulo {@code accesso}.
     */
    @EventListener
    @Transactional
    public void onUtenteRegistrato(UtenteRegistrato event) {
        String userId = event.userId();
        List<String> roles = event.roles();
        UUID tenantId = ContestoTenant.require();
        List<Map<String, Object>> assigned = jdbc.queryForList("""
                INSERT INTO notification_recipients (notification_id, tenant_id, user_id)
                SELECT n.id, n.tenant_id, ? FROM notifications n
                WHERE n.tenant_id = ? AND n.requires_ack
                  AND (n.audience_type = 'tenant' OR (n.audience_type = 'role' AND n.audience_value = ANY (?)))
                ON CONFLICT DO NOTHING
                RETURNING notification_id
                """, userId, tenantId, roles.toArray(String[]::new));
        for (Map<String, Object> row : assigned) {
            UUID notificationId = (UUID) row.get("notification_id");
            Boolean sendEmail = jdbc.queryForObject("SELECT send_email FROM notifications WHERE tenant_id = ? AND id = ?",
                    Boolean.class, tenantId, notificationId);
            if (Boolean.TRUE.equals(sendEmail)) {
                queueEmails(notificationId, List.of(userId));
            }
        }
    }

    private void validate(NuovaNotifica n) {
        switch (n.audience().type()) {
            case Pubblico.TENANT -> {
                if (n.audience().value() != null) {
                    throw new DestinatarioSconosciutoException("Per tutta l'azienda non va indicato un destinatario");
                }
            }
            case Pubblico.ROLE -> {
                if (Ruolo.fromCode(n.audience().value()).isEmpty()) {
                    throw new DestinatarioSconosciutoException("Ruolo sconosciuto: " + n.audience().value());
                }
            }
            case Pubblico.USER -> {
                Integer known = jdbc.queryForObject(
                        "SELECT count(*) FROM app_users WHERE tenant_id = ? AND user_id = ?", Integer.class,
                        ContestoTenant.require(), n.audience().value());
                if (known == null || known == 0) {
                    throw new DestinatarioSconosciutoException("Destinatario sconosciuto");
                }
            }
            default -> throw new DestinatarioSconosciutoException("Tipo di destinatario non valido");
        }
    }

    private void queueEmails(UUID notificationId, List<String> userIds) {
        if (!properties.email().enabled() || userIds.isEmpty()) {
            return;
        }
        UUID tenantId = ContestoTenant.require();
        Map<String, Object> n = jdbc.queryForMap("""
                SELECT n.title, n.body, n.requires_ack, n.sender_name, t.name AS tenant_name
                FROM notifications n JOIN tenants t ON t.id = n.tenant_id
                WHERE n.tenant_id = ? AND n.id = ?
                """, tenantId, notificationId);
        boolean requiresAck = (Boolean) n.get("requires_ack");
        String subject = "[" + n.get("tenant_name") + "] " + (requiresAck ? "Da confermare: " : "") + n.get("title");
        String text = emailText(notificationId, (String) n.get("title"), (String) n.get("body"),
                (String) n.get("sender_name"), requiresAck);

        for (String userId : userIds) {
            jdbc.update("""
                    INSERT INTO email_deliveries (id, tenant_id, notification_id, user_id, to_address, subject, body_text)
                    SELECT ?, tenant_id, ?, user_id, email, ?, ? FROM app_users
                    WHERE tenant_id = ? AND user_id = ? AND email IS NOT NULL AND email <> ''
                    """, UUID.randomUUID(), notificationId, subject, text, tenantId, userId);
        }
    }

    private String emailText(UUID id, String title, String body, String senderName, boolean requiresAck) {
        StringBuilder text = new StringBuilder();
        text.append(senderName == null ? "Civil Beefy" : senderName)
            .append(requiresAck ? " ti ha inviato una comunicazione che richiede la presa visione." : " ti ha inviato una comunicazione.")
            .append("\n\n").append(title).append("\n");
        if (properties.email().includeBody() && body != null && !body.isBlank()) {
            text.append("\n").append(body).append("\n");
        }
        String appUrl = properties.email().appUrl();
        if (appUrl != null && !appUrl.isBlank()) {
            text.append("\n").append(requiresAck ? "Apri Civil Beefy per leggerla e confermare la presa visione:"
                    : "Apri Civil Beefy per leggerla:")
                .append("\n").append(appUrl.replaceAll("/$", "")).append("/notifiche/").append(id).append("\n");
        } else if (requiresAck) {
            text.append("\nApri Civil Beefy per confermare la presa visione.\n");
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ destinatario

    @Transactional(readOnly = true)
    public List<NotificaUtente> listMine(String userId, boolean onlyOpen, int limit) {
        return jdbc.query("SELECT " + MY_COLUMNS + """
                FROM notification_recipients r JOIN notifications n ON n.id = r.notification_id
                WHERE r.tenant_id = ? AND r.user_id = ?
                  AND (NOT ? OR r.read_at IS NULL OR (n.requires_ack AND r.acknowledged_at IS NULL))
                ORDER BY n.created_at DESC
                LIMIT ?
                """, MY_ROW, ContestoTenant.require(), userId, onlyOpen, limit);
    }

    @Transactional(readOnly = true)
    public NotificaUtente getMine(String userId, UUID id) {
        List<NotificaUtente> found = jdbc.query("SELECT " + MY_COLUMNS + """
                FROM notification_recipients r JOIN notifications n ON n.id = r.notification_id
                WHERE r.tenant_id = ? AND r.user_id = ? AND r.notification_id = ?
                """, MY_ROW, ContestoTenant.require(), userId, id);
        if (found.isEmpty()) {
            throw new NotificaNonTrovataException();
        }
        return found.getFirst();
    }

    @Transactional(readOnly = true)
    public Counts countsMine(String userId) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE r.read_at IS NULL) AS unread,
                       count(*) FILTER (WHERE n.requires_ack AND r.acknowledged_at IS NULL) AS to_ack
                FROM notification_recipients r JOIN notifications n ON n.id = r.notification_id
                WHERE r.tenant_id = ? AND r.user_id = ?
                """, (rs, i) -> new Counts(rs.getLong("unread"), rs.getLong("to_ack")),
                ContestoTenant.require(), userId);
    }

    /**
     * Segna come letta e/o conferma la presa visione. Idempotente: le date restano quelle della
     * prima volta. La presa visione implica la lettura.
     */
    @Transactional
    public NotificaUtente updateMine(String userId, UUID id, boolean read, boolean acknowledged) {
        NotificaUtente current = getMine(userId, id);
        if (acknowledged && !current.requiresAck()) {
            throw new DestinatarioSconosciutoException("Questa notifica non richiede la presa visione");
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                UPDATE notification_recipients
                   SET read_at = CASE WHEN ? OR ? THEN coalesce(read_at, ?) ELSE read_at END,
                       acknowledged_at = CASE WHEN ? THEN coalesce(acknowledged_at, ?) ELSE acknowledged_at END
                 WHERE tenant_id = ? AND user_id = ? AND notification_id = ?
                """, read, acknowledged, now, acknowledged, now, ContestoTenant.require(), userId, id);
        return getMine(userId, id);
    }

    // ------------------------------------------------------------------ amministrazione

    @Transactional(readOnly = true)
    public List<RiepilogoComunicazione> listAnnouncements(int limit) {
        return jdbc.query(SUMMARY_SQL + " WHERE n.tenant_id = ? AND n.kind = 'announcement' "
                + SUMMARY_GROUP + " ORDER BY n.created_at DESC LIMIT ?", SUMMARY_ROW, ContestoTenant.require(), limit);
    }

    @Transactional(readOnly = true)
    public RiepilogoComunicazione getAnnouncement(UUID id) {
        List<RiepilogoComunicazione> found = jdbc.query(SUMMARY_SQL + " WHERE n.tenant_id = ? AND n.id = ? " + SUMMARY_GROUP,
                SUMMARY_ROW, ContestoTenant.require(), id);
        if (found.isEmpty()) {
            throw new NotificaNonTrovataException();
        }
        return found.getFirst();
    }

    /** Report della presa visione: un elemento per destinatario. */
    @Transactional(readOnly = true)
    public List<StatoDestinatario> recipients(UUID id) {
        getAnnouncement(id);
        return jdbc.query("""
                SELECT r.user_id, u.name, u.email, r.assigned_at, r.read_at, r.acknowledged_at,
                       (SELECT d.status FROM email_deliveries d
                         WHERE d.tenant_id = r.tenant_id AND d.notification_id = r.notification_id AND d.user_id = r.user_id
                         ORDER BY d.created_at DESC LIMIT 1) AS email_status
                FROM notification_recipients r
                JOIN app_users u ON u.tenant_id = r.tenant_id AND u.user_id = r.user_id
                WHERE r.tenant_id = ? AND r.notification_id = ?
                ORDER BY coalesce(u.name, u.username, u.user_id)
                """, (rs, i) -> new StatoDestinatario(rs.getString("user_id"), rs.getString("name"),
                rs.getString("email"), instant(rs.getTimestamp("assigned_at")), instant(rs.getTimestamp("read_at")),
                instant(rs.getTimestamp("acknowledged_at")), rs.getString("email_status")),
                ContestoTenant.require(), id);
    }

    private int recipientsCount(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients WHERE tenant_id = ? AND notification_id = ?",
                Integer.class, ContestoTenant.require(), id);
        return count == null ? 0 : count;
    }

    private static final String SUMMARY_SQL = """
            SELECT n.id, n.title, n.body, n.link, n.audience_type, n.audience_value, n.requires_ack, n.send_email,
                   n.sender_name, n.created_at,
                   count(r.user_id) AS recipients,
                   count(r.read_at) AS read,
                   count(r.acknowledged_at) AS acknowledged,
                   (SELECT count(*) FROM email_deliveries d WHERE d.tenant_id = n.tenant_id AND d.notification_id = n.id AND d.status = 'sent') AS emails_sent,
                   (SELECT count(*) FROM email_deliveries d WHERE d.tenant_id = n.tenant_id AND d.notification_id = n.id AND d.status = 'failed') AS emails_failed
            FROM notifications n
            LEFT JOIN notification_recipients r ON r.notification_id = n.id AND r.tenant_id = n.tenant_id
            """;
    private static final String SUMMARY_GROUP = " GROUP BY n.id ";

    private static final RowMapper<RiepilogoComunicazione> SUMMARY_ROW = (rs, i) -> new RiepilogoComunicazione(
            rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("body"), rs.getString("link"),
            rs.getString("audience_type"), rs.getString("audience_value"), rs.getBoolean("requires_ack"),
            rs.getBoolean("send_email"), rs.getString("sender_name"), instant(rs.getTimestamp("created_at")),
            rs.getLong("recipients"), rs.getLong("read"), rs.getLong("acknowledged"), rs.getLong("emails_sent"),
            rs.getLong("emails_failed"));

    private static final RowMapper<NotificaUtente> MY_ROW = (rs, i) -> new NotificaUtente(
            rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("type"), rs.getString("title"),
            rs.getString("body"), rs.getString("link"), rs.getString("sender_name"),
            instant(rs.getTimestamp("created_at")), rs.getBoolean("requires_ack"), instant(rs.getTimestamp("read_at")),
            instant(rs.getTimestamp("acknowledged_at")));

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
