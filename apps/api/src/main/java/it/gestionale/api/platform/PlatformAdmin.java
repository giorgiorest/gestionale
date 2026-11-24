package it.gestionale.api.platform;

import org.springframework.security.oauth2.jwt.Jwt;

/** Amministratore della piattaforma che sta facendo la richiesta (dal token del suo realm). */
public record PlatformAdmin(String id, String name, String email) {

    public static PlatformAdmin from(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = jwt.getClaimAsString("preferred_username");
        }
        return new PlatformAdmin(jwt.getSubject(), name, jwt.getClaimAsString("email"));
    }
}
