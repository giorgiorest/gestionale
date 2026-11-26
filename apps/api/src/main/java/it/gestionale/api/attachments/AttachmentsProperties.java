package it.gestionale.api.attachments;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione dell'archivio allegati (ADR 0016). Tutti i valori arrivano da variabili
 * d'ambiente: nessun default in produzione per {@code s3.bucket}, {@code s3.accessKey} e
 * {@code s3.secretKey} (vedi {@code application.yml}).
 */
@ConfigurationProperties("gestionale.attachments")
public record AttachmentsProperties(S3 s3, long maxSizeBytes, List<String> allowedMimeTypes,
                                     Duration presignedUrlDuration) {

    /**
     * @param endpoint vuoto = AWS S3 reale (nessun override); valorizzato per un archivio
     *                 self-hosted compatibile S3 (MinIO in sviluppo, altri in produzione)
     * @param forcePathStyle true per gli archivi self-hosted (es. {@code http://host:9000/bucket/key}),
     *                       false per S3 reale (virtual-hosted style)
     */
    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey,
                      boolean forcePathStyle) {
    }
}
