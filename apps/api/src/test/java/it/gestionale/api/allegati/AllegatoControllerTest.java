package it.gestionale.api.allegati;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.DatiDiTest;
import it.gestionale.api.JwtDiTest;
import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.licenze.LicenzaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * I byte non passano mai da questi endpoint (ADR 0016): qui si verifica solo che l'URL firmato
 * punti alla chiave giusta e che i metadati rispettino permesso, tenant e idempotenza — non che
 * un vero caricamento su S3/MinIO funzioni (fuori dal contratto di questa API).
 */
@AutoConfigureMockMvc
class AllegatoControllerTest extends TestIntegrazioneBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenzaRepository licenses;
    @Autowired
    AllegatoRepository attachments;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = DatiDiTest.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions createUploadUrl(JwtRequestPostProcessor jwt, String json) throws Exception {
        return mvc.perform(post("/api/attachments/upload-url")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions confirm(UUID tenant, String role, UUID id, String sha256) throws Exception {
        return mvc.perform(put("/api/attachments/" + id)
                .with(JwtDiTest.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sha256\":\"" + sha256 + "\"}"));
    }

    private String uploadUrlJson(UUID id, UUID entityId) {
        return """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"cantiere.jpg",
                 "mimeType":"image/jpeg","sizeBytes":1024}
                """.formatted(id, entityId);
    }

    /** Crea la riga "in caricamento" e la conferma, come farebbe un client reale. */
    private void createAndConfirm(UUID tenant, String role, UUID id, UUID entityId) throws Exception {
        createUploadUrl(JwtDiTest.user(tenant, role), uploadUrlJson(id, entityId)).andExpect(status().isOk());
        confirm(tenant, role, id, "a".repeat(64)).andExpect(status().isOk());
    }

    @Test
    void createsUploadUrlAndAPendingRowNotYetVisibleInAnyList() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        createUploadUrl(JwtDiTest.user(tenantA, "operator"), uploadUrlJson(id, entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.expiresAt").exists());

        // La riga esiste già (lega i due passi), ma non è confermata: non compare in nessun elenco.
        Allegato pending = ContestoTenant.callAs(tenantA, () -> attachments.findByIdAndTenantId(id, tenantA))
                .orElseThrow();
        assertThat(pending.isPending()).isTrue();
        mvc.perform(get("/api/attachments")
                        .param("entityType", "daily_reports")
                        .param("entityId", entityId.toString())
                        .with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void resendingUploadUrlWithTheSameDataReusesTheSameRow() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        createUploadUrl(JwtDiTest.user(tenantA, "operator"), uploadUrlJson(id, entityId)).andExpect(status().isOk());
        createUploadUrl(JwtDiTest.user(tenantA, "operator"), uploadUrlJson(id, entityId)).andExpect(status().isOk());

        createUploadUrl(JwtDiTest.user(tenantA, "operator"), """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"altro.jpg",
                 "mimeType":"application/pdf","sizeBytes":9999}
                """.formatted(id, entityId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDisallowedMimeTypeAndTooLargeFiles() throws Exception {
        UUID entityId = UUID.randomUUID();
        createUploadUrl(JwtDiTest.user(tenantA, "operator"), """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"virus.exe",
                 "mimeType":"application/x-msdownload","sizeBytes":1024}
                """.formatted(UUID.randomUUID(), entityId))
                .andExpect(status().isBadRequest());

        createUploadUrl(JwtDiTest.user(tenantA, "operator"), """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"enorme.jpg",
                 "mimeType":"image/jpeg","sizeBytes":999999999999}
                """.formatted(UUID.randomUUID(), entityId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmRequiresAPriorUploadUrlAndIsThenIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        // Nessuna richiesta di URL di caricamento prima: non c'è nulla da confermare.
        confirm(tenantA, "operator", id, "a".repeat(64)).andExpect(status().isNotFound());

        createUploadUrl(JwtDiTest.user(tenantA, "operator"), uploadUrlJson(id, entityId)).andExpect(status().isOk());
        confirm(tenantA, "operator", id, "a".repeat(64))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.entityType").value("daily_reports"))
                .andExpect(jsonPath("$.sha256").value("a".repeat(64)));
        // Reinvio dalla outbox: stesso id, nessun cambiamento.
        confirm(tenantA, "operator", id, "a".repeat(64))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/attachments")
                        .param("entityType", "daily_reports")
                        .param("entityId", entityId.toString())
                        .with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].originalFilename").value("cantiere.jpg"));
    }

    @Test
    void downloadUrlIsNotFoundForPendingMissingOrOtherTenantAttachment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        createUploadUrl(JwtDiTest.user(tenantA, "operator"), uploadUrlJson(id, entityId)).andExpect(status().isOk());

        // Ancora in caricamento: non scaricabile.
        mvc.perform(get("/api/attachments/" + id + "/download-url").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isNotFound());

        confirm(tenantA, "operator", id, "a".repeat(64)).andExpect(status().isOk());
        mvc.perform(get("/api/attachments/" + id + "/download-url").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").exists());

        mvc.perform(get("/api/attachments/" + id + "/download-url").with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/" + UUID.randomUUID() + "/download-url").with(JwtDiTest.user(tenantA, "admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        createAndConfirm(tenantA, "operator", id, UUID.randomUUID());

        mvc.perform(delete("/api/attachments/" + id).with(JwtDiTest.user(tenantA, "operator")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/attachments/" + id + "/download-url").with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isNotFound());
        // Reinvio dalla outbox: nessun errore.
        mvc.perform(delete("/api/attachments/" + id).with(JwtDiTest.user(tenantA, "operator")))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/attachments/" + UUID.randomUUID()).with(JwtDiTest.user(tenantA, "operator")))
                .andExpect(status().isNoContent());
    }

    @Test
    void viewerCannotWrite() throws Exception {
        confirm(tenantA, "viewer", UUID.randomUUID(), "a".repeat(64)).andExpect(status().isForbidden());
        createUploadUrl(JwtDiTest.user(tenantA, "viewer"), uploadUrlJson(UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/attachments/" + UUID.randomUUID()).with(JwtDiTest.user(tenantA, "viewer")))
                .andExpect(status().isForbidden());
    }

    /**
     * {@code amministrativo} ha {@code attachments:read/write} ma non {@code daily-reports:read}
     * (non deve mai vedere i rapportini): non deve poter vedere né scaricare né caricare allegati
     * appesi a un rapportino.
     */
    @Test
    void roleWithoutTheLinkedEntityPermissionCannotAccessItsAttachments() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        createAndConfirm(tenantA, "operator", id, entityId);

        mvc.perform(get("/api/attachments")
                        .param("entityType", "daily_reports")
                        .param("entityId", entityId.toString())
                        .with(JwtDiTest.user(tenantA, "amministrativo")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attachments/" + id + "/download-url").with(JwtDiTest.user(tenantA, "amministrativo")))
                .andExpect(status().isForbidden());
        createUploadUrl(JwtDiTest.user(tenantA, "amministrativo"), uploadUrlJson(UUID.randomUUID(), entityId))
                .andExpect(status().isForbidden());
    }

    @Test
    void attachmentsOfAnotherTenantAreNotVisible() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        createAndConfirm(tenantA, "operator", id, entityId);

        mvc.perform(get("/api/attachments")
                        .param("entityType", "daily_reports")
                        .param("entityId", entityId.toString())
                        .with(JwtDiTest.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
