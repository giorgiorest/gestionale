package it.gestionale.api.configurazione;

import it.gestionale.api.accesso.RuoloAutoritaConverter;
import it.gestionale.api.accesso.SicurezzaProperties;
import it.gestionale.api.osservabilita.ContestoLogRichiestaFilter;
import it.gestionale.api.tenant.AccessoTenantService;
import it.gestionale.api.tenant.TenantFilter;
import it.gestionale.api.tenant.TenantProperties;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API stateless protetta da JWT (OAuth2 resource server, provider OIDC: Keycloak).
 *
 * <ul>
 *   <li>issuer e audience del token sono validati (spring.security.oauth2.resourceserver.jwt.*);</li>
 *   <li>i ruoli del claim {@code roles} diventano autorità e permessi ({@link RuoloAutoritaConverter});</li>
 *   <li>il tenant viene estratto dal token da {@link TenantFilter}, subito dopo l'autenticazione;</li>
 *   <li>subito dopo, {@link ContestoLogRichiestaFilter} mette tenant e id di richiesta in MDC
 *       per i log strutturati (ADR 0022);</li>
 *   <li>i permessi si controllano sugli endpoint con {@code @PreAuthorize};</li>
 *   <li>CORS solo per le origini dell'app desktop ({@link CorsProperties}): token nel header, niente cookie.</li>
 * </ul>
 *
 * <p>Le operazioni della console di piattaforma ({@code /api/platform/**}) hanno una catena
 * propria, con un altro emittente dei token: vedi {@code PiattaformaSicurezzaConfig}.
 */
@Configuration
@EnableMethodSecurity
public class SicurezzaConfig {

    @Bean
    RuoloAutoritaConverter roleAuthoritiesConverter(SicurezzaProperties properties) {
        return new RuoloAutoritaConverter(properties.rolesClaim());
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(RuoloAutoritaConverter rolesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TenantProperties tenancyProperties,
                                            AccessoTenantService tenantAccess,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // /actuator/prometheus: un raccoglitore di metriche non ha un JWT; resta comunque
                // irraggiungibile da Internet perché non instradato da Caddy fuori dalla rete
                // amministrativa (vedi Caddyfile, stesso ADMIN_ALLOWED_IPS di console/piattaforma).
                .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health", "/actuator/info",
                        "/actuator/prometheus").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .addFilterAfter(new TenantFilter(tenancyProperties, tenantAccess), BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new ContestoLogRichiestaFilter(), TenantFilter.class);
        return http.build();
    }
}
