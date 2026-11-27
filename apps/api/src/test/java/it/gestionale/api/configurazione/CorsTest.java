package it.gestionale.api.configurazione;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** L'app desktop (origine Tauri) può chiamare l'API; le altre origini no. */
@AutoConfigureMockMvc
class CorsTest extends TestIntegrazioneBase {

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

    /**
     * Web app dietro nginx (stesso sito): le scritture hanno l'header Origin con l'indirizzo pubblico.
     * Con gli header X-Forwarded-* del proxy non devono essere scambiate per richieste cross-origin.
     */
    @Test
    void sameSiteWriteBehindReverseProxyIsNotRejected() throws Exception {
        mvc.perform(put("/api/customers/" + UUID.randomUUID())
                        .header("Origin", "https://gestionale.cliente.example")
                        .header("X-Forwarded-Host", "gestionale.cliente.example")
                        .header("X-Forwarded-Proto", "https")
                        .with(JwtDiTest.user(UUID.randomUUID(), "operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    void writeFromForeignSiteIsRejected() throws Exception {
        mvc.perform(put("/api/customers/" + UUID.randomUUID())
                        .header("Origin", "https://sito-malevolo.example")
                        .header("X-Forwarded-Host", "gestionale.cliente.example")
                        .header("X-Forwarded-Proto", "https")
                        .with(JwtDiTest.user(UUID.randomUUID(), "operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualRequestFromDesktopAppCarriesCorsHeader() throws Exception {
        mvc.perform(get("/api/me")
                        .header("Origin", "http://tauri.localhost")
                        .with(JwtDiTest.user(UUID.randomUUID(), "viewer")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://tauri.localhost"));
    }
}
