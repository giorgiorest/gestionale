package it.gestionale.api.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantRepository;
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
class AttachmentControllerTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;
    @Autowired
    AttachmentRepository attachments;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
        tenantB = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 0);
    }

    private ResultActions createUploadUrl(JwtRequestPostProcessor jwt, String json) throws Exception {
        return mvc.perform(post("/api/attachments/upload-url")
                .with(jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions confirm(UUID tenant, String role, UUID id, String json) throws Exception {
        return mvc.perform(put("/api/attachments/" + id)
                .with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String confirmJson(UUID entityId) {
        return """
                {"entityType":"daily_reports","entityId":"%s","filename":"cantiere.jpg",
                 "mimeType":"image/jpeg","sizeBytes":1024,
                 "sha256":"%s"}
                """.formatted(entityId, "a".repeat(64));
    }

    @Test
    void createsUploadUrlPointingToTheStorageKey() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        String body = """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"cantiere.jpg",
                 "mimeType":"image/jpeg","sizeBytes":1024}
                """.formatted(id, entityId);

        createUploadUrl(TestJwt.user(tenantA, "operator"), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
        // La riga di metadati non esiste ancora: il primo passo non scrive nulla.
        assertThat(TenantContext.callAs(tenantA, () -> attachments.findByIdAndTenantId(id, tenantA))).isEmpty();
    }

    @Test
    void rejectsDisallowedMimeTypeAndTooLargeFiles() throws Exception {
        UUID entityId = UUID.randomUUID();
        createUploadUrl(TestJwt.user(tenantA, "operator"), """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"virus.exe",
                 "mimeType":"application/x-msdownload","sizeBytes":1024}
                """.formatted(UUID.randomUUID(), entityId))
                .andExpect(status().isBadRequest());

        createUploadUrl(TestJwt.user(tenantA, "operator"), """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"enorme.jpg",
                 "mimeType":"image/jpeg","sizeBytes":999999999999}
                """.formatted(UUID.randomUUID(), entityId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmCreatesMetadataIdempotently() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        confirm(tenantA, "operator", id, confirmJson(entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.entityType").value("daily_reports"))
                .andExpect(jsonPath("$.version").value(0));
        // Reinvio dalla outbox: stesso id, nessun duplicato né cambiamento.
        confirm(tenantA, "operator", id, confirmJson(entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        mvc.perform(get("/api/attachments")
                        .param("entityType", "daily_reports")
                        .param("entityId", entityId.toString())
                        .with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].originalFilename").value("cantiere.jpg"));
    }

    @Test
    void downloadUrlIsNotFoundForMissingOrOtherTenantAttachment() throws Exception {
        UUID id = UUID.randomUUID();
        confirm(tenantA, "operator", id, confirmJson(UUID.randomUUID())).andExpect(status().isOk());

        mvc.perform(get("/api/attachments/" + id + "/download-url").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").exists());

        mvc.perform(get("/api/attachments/" + id + "/download-url").with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/" + UUID.randomUUID() + "/download-url").with(TestJwt.user(tenantA, "admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIsLogicalAndIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        confirm(tenantA, "operator", id, confirmJson(UUID.randomUUID())).andExpect(status().isOk());

        mvc.perform(delete("/api/attachments/" + id).with(TestJwt.user(tenantA, "operator")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/attachments/" + id + "/download-url").with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isNotFound());
        // Reinvio dalla outbox: nessun errore.
        mvc.perform(delete("/api/attachments/" + id).with(TestJwt.user(tenantA, "operator")))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/attachments/" + UUID.randomUUID()).with(TestJwt.user(tenantA, "operator")))
                .andExpect(status().isNoContent());
    }

    @Test
    void viewerCannotWrite() throws Exception {
        confirm(tenantA, "viewer", UUID.randomUUID(), confirmJson(UUID.randomUUID()))
                .andExpect(status().isForbidden());
        createUploadUrl(TestJwt.user(tenantA, "viewer"), """
                {"id":"%s","entityType":"daily_reports","entityId":"%s","filename":"x.jpg",
                 "mimeType":"image/jpeg","sizeBytes":10}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/attachments/" + UUID.randomUUID()).with(TestJwt.user(tenantA, "viewer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void attachmentsOfAnotherTenantAreNotVisible() throws Exception {
        UUID id = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        confirm(tenantA, "operator", id, confirmJson(entityId)).andExpect(status().isOk());

        mvc.perform(get("/api/attachments")
                        .param("entityType", "daily_reports")
                        .param("entityId", entityId.toString())
                        .with(TestJwt.user(tenantB, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
