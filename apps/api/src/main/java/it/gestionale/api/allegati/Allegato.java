package it.gestionale.api.allegati;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Metadati di un allegato (ADR 0016). L'id è generato dal client (UUID v7), come ogni entità.
 *
 * <p>I byte non sono qui: stanno nell'archivio compatibile S3 alla chiave {@code storageKey}.
 * Questa riga esiste solo per controllare permesso/tenant ed emettere un URL firmato — l'API non
 * fa mai da proxy per i byte.
 *
 * <p>La cancellazione è <strong>logica</strong>, come per le altre entità (ADR 0018): il tombstone
 * si propaga ai dispositivi offline. La rimozione fisica dell'oggetto nell'archivio è un processo
 * a parte (ADR 0016, "punti noti ancora aperti"), non qui.
 */
@Entity
@Table(name = "attachments")
public class Allegato {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** NULL finché l'allegato è "in caricamento": valorizzato da {@link #confirm}. */
    @Column
    private String sha256;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    /** Blocco ottimistico: incrementata da Hibernate a ogni modifica. */
    @Version
    @Column(nullable = false)
    private long version;

    /** Valorizzata = record cancellato (tombstone per la sincronizzazione). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Allegato() {
    }

    public Allegato(UUID id, UUID tenantId, String entityType, UUID entityId, String originalFilename,
                       String mimeType, long sizeBytes, String sha256, String storageKey, String userId) {
        this.id = id;
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.createdBy = userId;
        this.updatedBy = userId;
    }

    /** True finché l'URL di caricamento è stato emesso ma la conferma non è ancora arrivata. */
    public boolean isPending() {
        return sha256 == null;
    }

    /**
     * Conferma l'allegato dopo il caricamento: valorizza {@code sha256}. Non fa nulla se è già
     * confermato (idempotente, per il reinvio dalla outbox) — una volta confermato, un allegato
     * non si modifica più (solo cancellazione logica).
     */
    public void confirm(String sha256, String userId, Instant at) {
        if (!isPending()) {
            return;
        }
        this.sha256 = sha256;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    /**
     * Marca il record come cancellato. Non fa nulla se lo è già: la cancellazione è idempotente.
     * Non rimuove l'oggetto dall'archivio (ADR 0016): è un processo a parte.
     */
    public void markDeleted(String userId, Instant at) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = at;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
