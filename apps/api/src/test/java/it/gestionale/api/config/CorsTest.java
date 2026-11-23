package it.gestionale.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** L'app desktop (origine Tauri) può chiamare l'API; le altre origini no. */
@AutoConfigureMockMvc
class CorsTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"http://tauri.localhost", "tauri://localhost"})
    void preflightFromDesktopAppIsAllowed(String origin) throws Exception {
        mvc.perform(options("/api/customers")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/customers")
                        .header("Origin", "https://sito-malevolo.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualRequestFromDesktopAppCarriesCorsHeader() throws Exception {
        mvc.perform(get("/api/me")
                        .header("Origin", "http://tauri.localhost")
                        .with(TestJwt.user(UUID.randomUUID(), "viewer")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://tauri.localhost"));
    }
}
