package it.gestionale.api.licensing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Verifica le licenze offline: token {@code base64url(payload JSON) + "." + base64url(firma Ed25519)}.
 *
 * <p>La firma copre i byte del payload codificato. Il payload ha la forma:
 * <pre>{"tenantId":"…","plan":"pro","maxUsers":10,"features":["aiAssistant"],
 *  "aiQuotaMonthly":500,"validUntil":"2027-12-31T23:59:59Z"}</pre>
 *
 * <p>Qui si verifica soltanto: la generazione e la chiave privata restano fuori da questo progetto.
 */
@Component
public class LicenseVerifier {

    private static final String ALGORITHM = "Ed25519";

    private final LicensingProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LicenseVerifier(LicensingProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Licenza verificata: tenant a cui appartiene ed entitlement concessi. */
    public record VerifiedLicense(UUID tenantId, Entitlements entitlements) {
    }

    record Payload(UUID tenantId, String plan, int maxUsers, Set<String> features, int aiQuotaMonthly,
                   Instant validUntil) {
    }

    public VerifiedLicense verify(String token) {
        if (token == null) {
            throw new InvalidLicenseException("Licenza assente");
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 2) {
            throw new InvalidLicenseException("Formato della licenza non valido");
        }

        byte[] payloadBytes;
        byte[] signatureBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signatureBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidLicenseException("Codifica della licenza non valida", e);
        }

        if (!signatureMatches(parts[0].getBytes(StandardCharsets.US_ASCII), signatureBytes)) {
            throw new InvalidLicenseException("Firma della licenza non valida");
        }

        Payload payload;
        try {
            payload = objectMapper.readValue(payloadBytes, Payload.class);
        } catch (java.io.IOException e) {
            throw new InvalidLicenseException("Contenuto della licenza non valido", e);
        }
        if (payload.tenantId() == null || payload.plan() == null) {
            throw new InvalidLicenseException("Licenza incompleta");
        }
        if (payload.validUntil() != null && !payload.validUntil().isAfter(clock.instant())) {
            throw new InvalidLicenseException("Licenza scaduta");
        }

        return new VerifiedLicense(payload.tenantId(), new Entitlements(payload.plan(), payload.maxUsers(),
                payload.features(), payload.aiQuotaMonthly(), payload.validUntil()));
    }

    private boolean signatureMatches(byte[] signedContent, byte[] signatureBytes) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey());
            verifier.update(signedContent);
            return verifier.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private PublicKey publicKey() throws GeneralSecurityException {
        String encoded = properties.publicKey();
        if (encoded == null || encoded.isBlank()) {
            throw new InvalidLicenseException("Chiave pubblica di licenza non configurata (gestionale.licensing.public-key)");
        }
        byte[] der;
        try {
            der = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidLicenseException("Chiave pubblica di licenza non valida", e);
        }
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
    }
}
