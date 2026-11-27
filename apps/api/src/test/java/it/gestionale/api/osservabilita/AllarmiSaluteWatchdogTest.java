package it.gestionale.api.osservabilita;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;

/** Deve avvisare solo sulla transizione, non a ogni controllo periodico. */
@ExtendWith(MockitoExtension.class)
class AllarmiSaluteWatchdogTest {

    @Mock
    HealthEndpoint health;
    @Mock
    NotificatoreAllarmiSalute notifier;

    @Test
    void firstCheckNeverAlerts() {
        AllarmiSaluteWatchdog watchdog = new AllarmiSaluteWatchdog(health, notifier);

        watchdog.check(Status.UP);

        verify(notifier, never()).notify(anyString(), anyString());
    }

    @Test
    void alertsOnlyWhenStatusChanges() {
        AllarmiSaluteWatchdog watchdog = new AllarmiSaluteWatchdog(health, notifier);
        watchdog.check(Status.UP);

        watchdog.check(Status.UP);
        verify(notifier, never()).notify(anyString(), anyString());

        watchdog.check(Status.DOWN);
        verify(notifier).notify(any(), any());

        watchdog.check(Status.DOWN);
        verify(notifier).notify(any(), any()); // ancora una sola volta: nessun nuovo avviso

        watchdog.check(Status.UP);
        verify(notifier, times(2)).notify(any(), any());
    }
}
