package it.gestionale.api.attachments;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import it.gestionale.api.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Allegati (ADR 0016). I byte non passano mai da qui: questo servizio controlla permesso,
 * entitlement (a monte, nel controller) e tenant, poi emette un URL firmato di breve durata.
 *
 * <p>Flusso in due passi (ADR 0016 punto 8, pensato per l'uso offline):
 * <ol>
 *   <li>{@link #createUploadUrl}: valida tipo/dimensione, emette l'URL {@code PUT} firmato.
 *       Non scrive ancora nessuna riga: se il caricamento fallisce, non resta metadato orfano.</li>
 *   <li>Il client carica i byte direttamente sull'archivio con quell'URL.</li>
 *   <li>{@link #confirm}: crea la riga di metadati. Idempotente, per il reinvio dalla outbox.</li>
 * </ol>
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final AttachmentsProperties properties;
    private final S3Presigner presigner;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, AttachmentsProperties properties,
                              S3Presigner presigner, Clock clock) {
        this.attachments = attachments;
        this.properties = properties;
        this.presigner = presigner;
        this.clock = clock;
    }

    /** Allegati attivi di un record ("le foto di questo rapportino"), più recenti prima. */
    @Transactional(readOnly = true)
    public List<AttachmentDto> list(String entityType, UUID entityId) {
        return attachments
                .findByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        TenantContext.require(), entityType, entityId)
                .stream()
                .map(AttachmentDto::from)
                .toList();
    }

    /**
     * Valida tipo MIME e dimensione (ADR 0016 punto 7: il tipo dichiarato dal client non è
     * attendibile da solo) ed emette l'URL firmato di caricamento. La chiave nell'archivio è
     * deterministica dall'id: {@link #confirm} la ricalcola allo stesso modo, senza doverla
     * portarsi dietro tra le due chiamate.
     */
    public AttachmentDto.UploadUrlResponse createUploadUrl(AttachmentDto.UploadUrlRequest request) {
        validate(request.mimeType(), request.sizeBytes());
        UUID tenantId = TenantContext.require();
        String key = storageKey(tenantId, request.entityType(), request.entityId(), request.id());

        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlDuration())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(properties.s3().bucket())
                        .key(key)
                        .contentType(request.mimeType())
                        .contentLength(request.sizeBytes())
                        .build())
                .build());
        return new AttachmentDto.UploadUrlResponse(presigned.url().toString(), presigned.expiration());
    }

    /**
     * Crea la riga di metadati dopo il caricamento. Idempotente: un reinvio dalla outbox con lo
     * stesso id restituisce semplicemente la riga già creata, senza sovrascriverla — una volta
     * confermato, un allegato non si modifica più (solo cancellazione logica).
     */
    @Transactional
    public AttachmentDto confirm(UUID id, AttachmentDto.Confirm input, String userId) {
        validate(input.mimeType(), input.sizeBytes());
        UUID tenantId = TenantContext.require();
        Attachment attachment = attachments.findByIdAndTenantId(id, tenantId)
                .orElseGet(() -> attachments.save(new Attachment(id, tenantId, input.entityType(), input.entityId(),
                        input.filename().trim(), input.mimeType(), input.sizeBytes(), input.sha256(),
                        storageKey(tenantId, input.entityType(), input.entityId(), id), userId)));
        attachments.flush();
        return AttachmentDto.from(attachment);
    }

    /** URL firmato di scarico: 404 se l'allegato non esiste, è cancellato o è di un altro tenant. */
    @Transactional(readOnly = true)
    public AttachmentDto.DownloadUrlResponse createDownloadUrl(UUID id) {
        Attachment attachment = attachments.findByIdAndTenantId(id, TenantContext.require())
                .filter(a -> !a.isDeleted())
                .orElseThrow(AttachmentNotFoundException::new);

        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlDuration())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.s3().bucket())
                        .key(attachment.getStorageKey())
                        .build())
                .build());
        return new AttachmentDto.DownloadUrlResponse(presigned.url().toString(), presigned.expiration());
    }

    /**
     * Cancellazione logica, idempotente: come le altre entità. Non rimuove l'oggetto
     * dall'archivio (ADR 0016, "punti noti ancora aperti": un processo a parte se ne occuperà).
     */
    @Transactional
    public void delete(UUID id, String userId) {
        attachments.findByIdAndTenantId(id, TenantContext.require())
                .ifPresent(attachment -> attachment.markDeleted(userId, clock.instant()));
        attachments.flush();
    }

    private void validate(String mimeType, long sizeBytes) {
        if (properties.allowedMimeTypes().stream().noneMatch(allowed -> allowed.equalsIgnoreCase(mimeType))) {
            throw new InvalidAttachmentException(
                    "Tipo di file non ammesso: " + mimeType.toLowerCase(Locale.ROOT));
        }
        if (sizeBytes > properties.maxSizeBytes()) {
            throw new InvalidAttachmentException(
                    "File troppo grande: il massimo è " + properties.maxSizeBytes() + " byte");
        }
    }

    /** Struttura della chiave nell'archivio (ADR 0016 punto 5): il tenant è il primo segmento. */
    private static String storageKey(UUID tenantId, String entityType, UUID entityId, UUID attachmentId) {
        return tenantId + "/" + entityType + "/" + entityId + "/" + attachmentId;
    }
}
