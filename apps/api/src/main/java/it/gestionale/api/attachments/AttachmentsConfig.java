package it.gestionale.api.attachments;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link S3Presigner}: genera URL firmati senza fare chiamate di rete (la firma è un calcolo
 * locale, ADR 0016 punto 4 — "i byte non passano mai dall'API"). Nessun {@code S3Client}: il
 * backend non carica né scarica mai byte, quindi non gli serve.
 */
@Configuration
public class AttachmentsConfig {

    @Bean
    public S3Presigner s3Presigner(AttachmentsProperties properties) {
        AttachmentsProperties.S3 s3 = properties.s3();
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.forcePathStyle())
                        .build());
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint()));
        }
        return builder.build();
    }
}
