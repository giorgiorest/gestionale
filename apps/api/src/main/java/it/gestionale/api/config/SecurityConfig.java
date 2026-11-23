package it.gestionale.api.config;

import it.gestionale.api.auth.RoleAuthoritiesConverter;
import it.gestionale.api.auth.SecurityProperties;
import it.gestionale.api.tenancy.TenancyProperties;
import it.gestionale.api.tenancy.TenantFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API stateless protetta da JWT (OAuth2 resource server, provider OIDC: Keycloak).
 *
 * <ul>
 *   <li>issuer e audience del token sono validati (spring.security.oauth2.resourceserver.jwt.*);</li>
 *   <li>i ruoli del claim {@code roles} diventano autorità e permessi ({@link RoleAuthoritiesConverter});</li>
 *   <li>il tenant viene estratto dal token da {@link TenantFilter}, subito dopo l'autenticazione;</li>
 *   <li>i permessi si controllano sugli endpoint con {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    RoleAuthoritiesConverter roleAuthoritiesConverter(SecurityProperties properties) {
        return new RoleAuthoritiesConverter(properties.rolesClaim());
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(RoleAuthoritiesConverter rolesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TenancyProperties tenancyProperties,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .addFilterAfter(new TenantFilter(tenancyProperties), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
