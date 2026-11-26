package it.gestionale.api.observability;

import it.gestionale.api.notifications.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Avviso agli amministratori dell'installazione: sempre nei log (ERROR), e via email se
 * {@code gestionale.observability.health-alert.to} è impostata (mittente e server SMTP riusano
 * la configurazione delle notifiche, {@code gestionale.notifications.email}).
 *
 * <p>Non passa da {@code NotificationService}: è stato dell'applicazione (un'installazione), non
 * un dato di dominio di un tenant, quindi non ha senso accodarlo per-tenant.
 *
 * <p>{@link JavaMailSender} arriva come {@link ObjectProvider}, non come dipendenza diretta:
 * Spring Boot crea quel bean solo se è configurato un server SMTP ({@code spring.mail.host}), che
 * qui è facoltativo (come per {@code EmailDispatcher}) — senza email configurata l'avviso resta
 * comunque nei log.
 */
@Component
public class HealthAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(HealthAlertNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObservabilityProperties properties;
    private final NotificationProperties notificationProperties;

    public HealthAlertNotifier(ObjectProvider<JavaMailSender> mailSender, ObservabilityProperties properties,
                               NotificationProperties notificationProperties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.notificationProperties = notificationProperties;
    }

    public void notify(String subject, String body) {
        log.error("{}: {}", subject, body);
        String to = properties.healthAlert().to();
        if (!StringUtils.hasText(to)) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("HEALTH_ALERT_EMAIL impostata ma nessun server SMTP configurato: avviso solo nei log.");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(notificationProperties.email().from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (RuntimeException e) {
            log.warn("Invio dell'email di avviso salute non riuscito: {}", e.getMessage());
        }
    }
}
