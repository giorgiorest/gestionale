package it.gestionale.api.osservabilita;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import it.gestionale.api.notifiche.NotificheProperties;
import it.gestionale.api.notifiche.NotificheProperties.Email;
import it.gestionale.api.notifiche.NotificheProperties.RapportinoMancante;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Senza {@code spring.mail.host} configurato, Spring Boot non crea alcun bean
 * {@link JavaMailSender} (come per {@code InviatoreEmail}): l'avviso deve restare possibile
 * (solo nei log) invece di far fallire il contesto o lanciare un'eccezione.
 */
@ExtendWith(MockitoExtension.class)
class NotificatoreAllarmiSaluteTest {

    @Mock
    ObjectProvider<JavaMailSender> mailSenderProvider;

    @Test
    void withoutToConfiguredNeverAsksForAMailSender() {
        OsservabilitaProperties properties = new OsservabilitaProperties(new OsservabilitaProperties.AllarmeSalute(null, null));
        NotificatoreAllarmiSalute notifier = new NotificatoreAllarmiSalute(mailSenderProvider, properties, notificationProperties());

        notifier.notify("oggetto", "corpo");

        verify(mailSenderProvider, never()).getIfAvailable();
    }

    @Test
    void withToConfiguredButNoMailSenderBeanDoesNotThrow() {
        OsservabilitaProperties properties =
                new OsservabilitaProperties(new OsservabilitaProperties.AllarmeSalute("admin@example.com", null));
        NotificatoreAllarmiSalute notifier = new NotificatoreAllarmiSalute(mailSenderProvider, properties, notificationProperties());

        notifier.notify("oggetto", "corpo"); // getIfAvailable() non stubbato: restituisce null, come senza bean

        verify(mailSenderProvider, times(1)).getIfAvailable();
    }

    private static NotificheProperties notificationProperties() {
        return new NotificheProperties(new Email(false, "Civil Beefy <noreply@localhost>", null, true, null, 5),
                new RapportinoMancante(null));
    }
}
