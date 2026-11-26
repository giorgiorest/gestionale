package it.gestionale.api.attachments;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allegati del tenant corrente (ADR 0016). I byte non passano mai da qui: gli endpoint emettono
 * solo URL firmati, oppure confermano o cancellano i metadati.
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    /** Allegati di un record, es. {@code ?entityType=daily_reports&entityId=...}. */
    @GetMapping
    @PreAuthorize("hasAuthority('attachments:read')")
    public List<AttachmentDto> list(@RequestParam String entityType, @RequestParam UUID entityId) {
        return service.list(entityType, entityId);
    }

    /** Primo passo del caricamento: valida tipo/dimensione ed emette l'URL firmato {@code PUT}. */
    @PostMapping("/upload-url")
    @PreAuthorize("hasAuthority('attachments:write')")
    public AttachmentDto.UploadUrlResponse createUploadUrl(@AuthenticationPrincipal Jwt jwt,
                                                             @Valid @RequestBody AttachmentDto.UploadUrlRequest request) {
        return service.createUploadUrl(request, jwt.getSubject());
    }

    /**
     * Secondo passo: conferma i metadati dopo il caricamento. Idempotente, come ogni endpoint
     * {@code PUT /{id}} del progetto: compatibile con il reinvio generico dalla outbox.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('attachments:write')")
    public AttachmentDto confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody AttachmentDto.Confirm input) {
        return service.confirm(id, input, jwt.getSubject());
    }

    /** URL firmato {@code GET} per scaricare i byte direttamente dall'archivio. */
    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAuthority('attachments:read')")
    public AttachmentDto.DownloadUrlResponse downloadUrl(@PathVariable UUID id) {
        return service.createDownloadUrl(id);
    }

    /** Cancellazione logica, idempotente: risponde 204 anche se l'allegato non esiste. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('attachments:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
