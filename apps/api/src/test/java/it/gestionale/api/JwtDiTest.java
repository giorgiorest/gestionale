package it.gestionale.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import it.gestionale.api.accesso.RuoloAutoritaConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** JWT di test con tenant e ruoli, convertiti in autorità come in produzione. */
public final class JwtDiTest {

    private static final RuoloAutoritaConverter CONVERTER = new RuoloAutoritaConverter("roles");

    private JwtDiTest() {
    }

    public static JwtRequestPostProcessor user(UUID tenant, String... roles) {
        return build(tenant, "user-" + String.join("-", roles), "mario.rossi", "Mario Rossi", "mario.rossi@example.com", roles);
    }

    /** Utente con identità esplicita (sub, nome, email). */
    public static JwtRequestPostProcessor person(UUID tenant, String subject, String name, String email,
                                                 String... roles) {
        return build(tenant, subject, subject, name, email, roles);
    }

    private static JwtRequestPostProcessor build(UUID tenant, String subject, String username, String name,
                                                 String email, String... roles) {
        return jwt()
                .jwt(j -> j.subject(subject)
                        .claim("preferred_username", username)
                        .claim("name", name)
                        .claim("email", email)
                        .claim("tenant_id", tenant.toString())
                        .claim("roles", List.of(roles)))
                .authorities(CONVERTER);
    }
}
