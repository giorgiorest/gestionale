package it.gestionale.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;

import it.gestionale.api.auth.RoleAuthoritiesConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** JWT di test con tenant e ruoli, convertiti in autorità come in produzione. */
public final class TestJwt {

    private static final RoleAuthoritiesConverter CONVERTER = new RoleAuthoritiesConverter("roles");

    private TestJwt() {
    }

    public static JwtRequestPostProcessor user(UUID tenant, String... roles) {
        return jwt()
                .jwt(j -> j.subject("user-" + String.join("-", roles))
                        .claim("preferred_username", "mario.rossi")
                        .claim("name", "Mario Rossi")
                        .claim("email", "mario.rossi@example.com")
                        .claim("tenant_id", tenant.toString())
                        .claim("roles", List.of(roles)))
                .authorities(CONVERTER);
    }
}
