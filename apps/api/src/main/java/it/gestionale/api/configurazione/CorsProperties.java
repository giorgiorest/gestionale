package it.gestionale.api.configurazione;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Origini ammesse per le chiamate cross-origin all'API.
 *
 * <p>La web app è servita dallo stesso dominio dell'API (nginx) e non ne ha bisogno.
 * Servono all'app desktop, che gira su un'origine propria: {@code http://tauri.localhost}
 * (Windows) e {@code tauri://localhost} (macOS/Linux). Si estendono con {@code CORS_ALLOWED_ORIGINS}.
 */
@ConfigurationProperties("gestionale.cors")
public record CorsProperties(@DefaultValue({"http://tauri.localhost", "tauri://localhost"}) List<String> allowedOrigins) {
}
