package it.gestionale.api.salute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.gestionale.api.accesso.SicurezzaProperties;
import it.gestionale.api.configurazione.CorsProperties;
import it.gestionale.api.configurazione.SicurezzaConfig;
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

@WebMvcTest(SaluteController.class)
@Import(SicurezzaConfig.class)
@EnableConfigurationProperties({TenantProperties.class, SicurezzaProperties.class, CorsProperties.class})
class SaluteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    AccessoTenantService tenantAccess;

    @Test
    void healthIsPublicAndReturnsOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\"}", true));
    }
}
