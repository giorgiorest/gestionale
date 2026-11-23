package it.gestionale.api.notifications;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Abilita i processi pianificati (invio email). */
@Configuration
@EnableScheduling
public class NotificationConfig {
}
