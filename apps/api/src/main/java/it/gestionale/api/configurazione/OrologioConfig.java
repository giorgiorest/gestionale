package it.gestionale.api.configurazione;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Orologio iniettabile, sostituibile nei test. */
@Configuration
public class OrologioConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
