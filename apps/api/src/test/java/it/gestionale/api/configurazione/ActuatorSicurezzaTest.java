package it.gestionale.api.configurazione;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.gestionale.api.accesso.SicurezzaProperties;
import it.gestionale.api.salute.SaluteController;
import it.gestionale.api.tenant.AccessoTenantService;
import it.gestionale.api.tenant.TenantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code /actuator/prometheus} non richiede un JWT (un raccoglitore di metriche non ne ha uno):
 * resta comunque irraggiungibile da Internet perché non instradato da Caddy fuori dalla rete
 * amministrativa (Caddyfile). Le altre rotte restano protette come prima.
 */
@WebMvcTest(SaluteController.class)
@Import(SicurezzaConfig.class)
@EnableConfigurationProperties({TenantProperties.class, SicurezzaProperties.class, CorsProperties.class})
class ActuatorSicurezzaTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    AccessoTenantService tenantAccess;

    @Test
    void prometheusEndpointDoesNotRequireAuthentication() throws Exception {
        // 404: nessun endpoint actuator registrato in questa slice (@WebMvcTest), ma non 401/403:
        // conferma che il filtro di sicurezza lo lascia passare.
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
    }

    @Test
    void domainEndpointsStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
    }
}
