package it.gestionale.api.notifiche;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.tenant.ContestoWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invia le email accodate in {@code email_deliveries}, per tutti i tenant.
 *
 * <p>Lavora come {@code app.worker = 'email-dispatcher'}: la policy RLS dedicata gli mostra solo
 * la coda email, non le tabelle di dominio. Più istanze dell'API possono girare insieme
 * ({@code FOR UPDATE SKIP LOCKED}). Gli errori vengono ritentati con attesa crescente fino a
 * {@code max-attempts}, poi la consegna è segnata come fallita (visibile nel report).
 */
@Component
@ConditionalOnProperty(name = "gestionale.notifications.email.enabled", havingValue = "true")
public class InviatoreEmail {

    static final String WORKER = "email-dispatcher";
    private static final Logger log = LoggerFactory.getLogger(InviatoreEmail.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JavaMailSender mailSender;
    private final NotificheProperties properties;
    private final Clock clock;

    public InviatoreEmail(JdbcTemplate jdbc, TransactionTemplate tx, JavaMailSender mailSender,
                           NotificheProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
    }

    private record Delivery(UUID id, String to, String subject, String body, int attempts) {
    }

    @Scheduled(fixedDelayString = "${gestionale.notifications.email.poll-interval:30s}", initialDelayString = "10s")
    public void run() {
        try {
            dispatch(50);
        } catch (RuntimeException e) {
            log.warn("Invio email non riuscito: {}", e.getMessage());
        }
    }

    /** Invia fino a {@code batch} email in attesa. Restituisce quante sono state inviate. */
    public int dispatch(int batch) {
        Integer sent = ContestoWorker.callAs(WORKER, () -> tx.execute(status -> {
            List<Delivery> pending = jdbc.query("""
                    SELECT id, to_address, subject, body_text, attempts FROM email_deliveries
                    WHERE status = 'pending' AND next_attempt_at <= ?
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """, (rs, i) -> new Delivery(rs.getObject("id", UUID.class), rs.getString("to_address"),
                    rs.getString("subject"), rs.getString("body_text"), rs.getInt("attempts")),
                    Timestamp.from(clock.instant()), batch);
            int ok = 0;
            for (Delivery d : pending) {
                if (deliver(d)) {
                    ok++;
                }
            }
            return ok;
        }));
        return sent == null ? 0 : sent;
    }

    private boolean deliver(Delivery d) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.email().from());
            message.setTo(d.to());
            message.setSubject(d.subject());
            message.setText(d.body());
            mailSender.send(message);
            jdbc.update("UPDATE email_deliveries SET status = 'sent', sent_at = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?",
                    Timestamp.from(clock.instant()), d.id());
            return true;
        } catch (RuntimeException e) {
            int attempts = d.attempts() + 1;
            boolean giveUp = attempts >= properties.email().maxAttempts();
            Duration backoff = Duration.ofMinutes((long) Math.pow(2, attempts));
            jdbc.update("UPDATE email_deliveries SET status = ?, attempts = ?, next_attempt_at = ?, last_error = ? WHERE id = ?",
                    giveUp ? "failed" : "pending", attempts, Timestamp.from(clock.instant().plus(backoff)),
                    truncate(e.getMessage()), d.id());
            log.warn("Email {} non inviata (tentativo {}): {}", d.id(), attempts, e.getMessage());
            return false;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "errore sconosciuto";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
