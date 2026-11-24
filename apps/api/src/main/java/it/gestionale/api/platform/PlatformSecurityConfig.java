package it.gestionale.api.platform;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Catena di sicurezza della console di piattaforma: solo {@code /api/platform/**}.
 *
 * <ul>
 *   <li>accetta SOLO token dell'emittente degli amministratori (realm separato), con l'audience
 *       della console: i token dei dipendenti (realm delle aziende) hanno un altro issuer e
 *       un'altra firma, quindi vengono rifiutati (401);</li>
 *   <li>ogni richiesta richiede l'autorità {@link #PLATFORM_ADMIN}, concessa solo dal ruolo
 *       {@code platform-admin} in un token di quell'emittente;</li>
 *   <li>nessun tenant nel contesto: i dati di tutte le aziende si leggono con
 *       {@code app.worker = 'platform-admin'} e le policy RLS dedicate (migrazione V5).</li>
 * </ul>
 * Viene valutata prima della catena dell'applicazione ({@link Order} 1).
 */
@Configuration
public class PlatformSecurityConfig {

    public static final String PLATFORM_ADMIN = "platform:admin";

    @Bean
    @Order(1)
    SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, PlatformProperties properties) throws Exception {
        PlatformProperties.Jwt jwt = properties.jwt();
        http
            .securityMatcher("/api/platform/**")
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(PLATFORM_ADMIN))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(j -> j
                    .decoder(decoder(jwt))
                    .jwtAuthenticationConverter(authenticationConverter(jwt))));
        return http.build();
    }

    /** Decoder dell'emittente degli amministratori: issuer e audience validati. Creato alla prima richiesta. */
    static JwtDecoder decoder(PlatformProperties.Jwt jwt) {
        return new SupplierJwtDecoder(() -> {
            if (isBlank(jwt.issuerUri())) {
                throw new JwtException("Console di piattaforma non configurata (gestionale.platform.jwt.issuer-uri)");
            }
            NimbusJwtDecoder decoder = isBlank(jwt.jwkSetUri())
                    ? NimbusJwtDecoder.withIssuerLocation(jwt.issuerUri()).build()
                    : NimbusJwtDecoder.withJwkSetUri(jwt.jwkSetUri()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(jwt.issuerUri()),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                            aud -> aud != null && aud.contains(jwt.audience()))));
            return decoder;
        });
    }

    /** Autorità della console: solo con il ruolo di amministratore e l'issuer atteso. */
    public static JwtAuthenticationConverter authenticationConverter(PlatformProperties.Jwt jwt) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities(jwt));
        return converter;
    }

    public static Converter<Jwt, Collection<GrantedAuthority>> authorities(PlatformProperties.Jwt jwt) {
        return token -> {
            List<String> roles = token.getClaimAsStringList("roles");
            boolean issuerOk = token.getIssuer() != null && Objects.equals(token.getIssuer().toString(), jwt.issuerUri());
            boolean admin = roles != null && roles.contains(jwt.adminRole());
            return issuerOk && admin ? List.of(new SimpleGrantedAuthority(PLATFORM_ADMIN)) : List.of();
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
