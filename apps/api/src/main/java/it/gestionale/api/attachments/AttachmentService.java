package it.gestionale.api.attachments;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import java.util.stream.Collectors;

import it.gestionale.api.auth.Permission;
import it.gestionale.api.tenancy.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * Allegati (ADR 0016). I byte non passano mai da qui: questo servizio controlla il permesso
 * generico, il permesso dell'entità a cui l'allegato è appeso e il tenant, poi emette un URL
 * firmato di breve durata. Non c'è un controllo di {@code EntitlementService}: oggi nessuna
 * entità di dominio (clienti, fornitori, rapportini, ...) è una funzionalità di piano a
 * pagamento — l'unica che lo è resta l'assistente AI (vedi {@code licensing/Features}).
 *
 * <p>Flusso in due passi (ADR 0016 punto 8, pensato per l'uso offline):
 * <ol>
 *   <li>{@link #createUploadUrl}: valida tipo/dimensione, crea la riga "in caricamento"
 *       ({@code sha256} NULL, non ancora visibile in nessun elenco) ed emette l'URL {@code PUT}
 *       firmato per la chiave di quella riga. Fissare la riga qui, non alla conferma, è ciò che
 *       lega i due passi: la conferma non ha più una copia propria di entità/tipo/dimensione da
 *       cui potrebbe discostarsi.</li>
 *   <li>Il client carica i byte direttamente sull'archivio con quell'URL.</li>
 *   <li>{@link #confirm}: valorizza {@code sha256}. Idempotente, per il reinvio dalla outbox.</li>
 * </ol>
 */
@Service
public class AttachmentService {

    /**
     * Permesso di lettura dell'entità a cui un allegato è appeso: non basta {@code
     * attachments:read}, altrimenti un ruolo come {@code amministrativo} (che ha
     * {@code attachments:read/write} ma non {@code daily-reports:read}) potrebbe vedere gli
     * allegati di un rapportino che non può leggere. Un {@code entityType} assente da questa
     * mappa non è raggiungibile da nessuno (difesa in profondità contro un valore non previsto).
     */
    private static final Map<String, Permission> ENTITY_READ_PERMISSION = Map.ofEntries(
            Map.entry("customers", Permission.CUSTOMERS_READ),
            Map.entry("suppliers", Permission.SUPPLIERS_READ),
            Map.entry("resources", Permission.RESOURCES_READ),
            Map.entry("commesse", Permission.COMMESSE_READ),
            Map.entry("resource_assignments", Permission.RESOURCE_ASSIGNMENTS_READ),
            Map.entry("work_activities", Permission.WORK_ACTIVITIES_READ),
            Map.entry("daily_reports", Permission.DAILY_REPORTS_READ),
            Map.entry("work_entries", Permission.WORK_ENTRIES_READ),
            Map.entry("daily_report_materials", Permission.DAILY_REPORT_MATERIALS_READ));

    /** Permesso di scrittura dell'entità a cui un allegato è appeso, stessa idea della lettura. */
    private static final Map<String, Permission> ENTITY_WRITE_PERMISSION = Map.ofEntries(
            Map.entry("customers", Permission.CUSTOMERS_WRITE),
            Map.entry("suppliers", Permission.SUPPLIERS_WRITE),
            Map.entry("resources", Permission.RESOURCES_WRITE),
            Map.entry("commesse", Permission.COMMESSE_WRITE),
            Map.entry("resource_assignments", Permission.RESOURCE_ASSIGNMENTS_WRITE),
            Map.entry("work_activities", Permission.WORK_ACTIVITIES_WRITE),
            Map.entry("daily_reports", Permission.DAILY_REPORTS_WRITE),
            Map.entry("work_entries", Permission.WORK_ENTRIES_WRITE),
            Map.entry("daily_report_materials", Permission.DAILY_REPORT_MATERIALS_WRITE));

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

    /** Allegati confermati e attivi di un record ("le foto di questo rapportino"), più recenti prima. */
    @Transactional(readOnly = true)
    public List<AttachmentDto> list(String entityType, UUID entityId) {
        requireEntityPermission(entityType, ENTITY_READ_PERMISSION, authoritiesOrEmpty());
        return attachments
                .findByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullAndSha256IsNotNullOrderByCreatedAtDesc(
                        TenantContext.require(), entityType, entityId)
                .stream()
                .map(AttachmentDto::from)
                .toList();
    }

    /**
     * Valida tipo MIME e dimensione (ADR 0016 punto 7: il tipo dichiarato dal client non è
     * attendibile da solo), crea la riga "in caricamento" ed emette l'URL firmato di caricamento.
     * Idempotente: un reinvio con lo stesso id e gli stessi dati riusa la riga già creata (stessa
     * chiave, stesso URL); con dati diversi è un errore, non un allegato diverso sotto lo stesso id.
     */
    @Transactional
    public AttachmentDto.UploadUrlResponse createUploadUrl(AttachmentDto.UploadUrlRequest request, String userId) {
        requireEntityPermission(request.entityType(), ENTITY_WRITE_PERMISSION, authoritiesOrEmpty());
        validate(request.mimeType(), request.sizeBytes());
        UUID tenantId = TenantContext.require();

        Attachment attachment = attachments.findByIdAndTenantId(request.id(), tenantId)
                .map(existing -> requireSameUpload(existing, request))
                .orElseGet(() -> attachments.save(new Attachment(request.id(), tenantId, request.entityType(),
                        request.entityId(), request.filename().trim(), request.mimeType(), request.sizeBytes(), null,
                        storageKey(tenantId, request.entityType(), request.entityId(), request.id()), userId)));
        attachments.flush();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlDuration())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(properties.s3().bucket())
                        .key(attachment.getStorageKey())
                        .contentType(attachment.getMimeType())
                        .contentLength(attachment.getSizeBytes())
                        .build())
                .build());
        return new AttachmentDto.UploadUrlResponse(presigned.url().toString(), presigned.expiration());
    }

    /**
     * Valorizza {@code sha256} dopo il caricamento. Idempotente: un reinvio dalla outbox con lo
     * stesso id non fa nulla se è già confermato — una volta confermato, un allegato non si
     * modifica più (solo cancellazione logica). Richiede una riga già creata da
     * {@link #createUploadUrl}: senza una richiesta di URL di caricamento prima, non c'è nulla da
     * confermare.
     */
    @Transactional
    public AttachmentDto confirm(UUID id, AttachmentDto.Confirm input, String userId) {
        Attachment attachment = attachments.findByIdAndTenantId(id, TenantContext.require())
                .filter(a -> !a.isDeleted())
                .orElseThrow(AttachmentNotFoundException::new);
        requireEntityPermission(attachment.getEntityType(), ENTITY_WRITE_PERMISSION, authoritiesOrEmpty());
        attachment.confirm(input.sha256(), userId, clock.instant());
        attachments.flush();
        return AttachmentDto.from(attachment);
    }

    /** URL firmato di scarico: 404 se l'allegato non esiste, non è confermato, è cancellato o è di un altro tenant. */
    @Transactional(readOnly = true)
    public AttachmentDto.DownloadUrlResponse createDownloadUrl(UUID id) {
        Attachment attachment = attachments.findByIdAndTenantId(id, TenantContext.require())
                .filter(a -> !a.isDeleted() && !a.isPending())
                .orElseThrow(AttachmentNotFoundException::new);
        requireEntityPermission(attachment.getEntityType(), ENTITY_READ_PERMISSION, authoritiesOrEmpty());

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
        attachments.findByIdAndTenantId(id, TenantContext.require()).ifPresent(attachment -> {
            requireEntityPermission(attachment.getEntityType(), ENTITY_WRITE_PERMISSION, authoritiesOrEmpty());
            attachment.markDeleted(userId, clock.instant());
        });
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

    /**
     * Un reinvio di {@link #createUploadUrl} con lo stesso id deve descrivere lo stesso
     * caricamento, altrimenti l'id non identifica più un solo allegato. Confrontare solo i campi
     * che contano per la chiave e per la firma dell'URL (non il nome, puramente descrittivo).
     */
    private static Attachment requireSameUpload(Attachment existing, AttachmentDto.UploadUrlRequest request) {
        if (existing.isDeleted()) {
            throw new InvalidAttachmentException("Id già usato da un allegato cancellato");
        }
        boolean same = existing.getEntityType().equals(request.entityType())
                && existing.getEntityId().equals(request.entityId())
                && existing.getMimeType().equalsIgnoreCase(request.mimeType())
                && existing.getSizeBytes() == request.sizeBytes();
        if (!same) {
            throw new InvalidAttachmentException("Id già usato per un caricamento con dati diversi");
        }
        return existing;
    }

    private static void requireEntityPermission(String entityType, Map<String, Permission> byEntityType,
                                                  Set<String> authorities) {
        Permission required = byEntityType.get(entityType);
        if (required == null || !authorities.contains(required.code())) {
            throw new AccessDeniedException("Permesso mancante per l'entità: " + entityType);
        }
    }

    /** Le autorità dell'utente autenticato, lette dal contesto di sicurezza (come {@code SyncController}). */
    private static Set<String> authoritiesOrEmpty() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** Struttura della chiave nell'archivio (ADR 0016 punto 5): il tenant è il primo segmento. */
    private static String storageKey(UUID tenantId, String entityType, UUID entityId, UUID attachmentId) {
        return tenantId + "/" + entityType + "/" + entityId + "/" + attachmentId;
    }
}
