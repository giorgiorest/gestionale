package it.gestionale.api.licenze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificatoreLicenzeTest {

    static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    static final UUID TENANT = UUID.fromString("0190a6f0-0000-7000-8000-000000000001");

    KeyPair keys;
    VerificatoreLicenze verifier;

    @BeforeEach
    void setUp() throws Exception {
        // Coppia di chiavi generata solo per il test: la chiave privata reale non è mai nel progetto.
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = verifierWithKey(keys);
    }

    private static VerificatoreLicenze verifierWithKey(KeyPair keyPair) {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new VerificatoreLicenze(new LicenzeProperties(publicKey), mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String payload(String validUntil) {
        return """
                {"tenantId":"%s","plan":"pro","maxUsers":10,"features":["aiAssistant"],
                 "aiQuotaMonthly":500,"validUntil":"%s"}""".formatted(TENANT, validUntil);
    }

    private static String sign(String json, PrivateKey key) throws Exception {
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(encodedPayload.getBytes(StandardCharsets.US_ASCII));
        return encodedPayload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    @Test
    void acceptsValidLicense() throws Exception {
        var license = verifier.verify(sign(payload("2027-01-01T00:00:00Z"), keys.getPrivate()));

        assertThat(license.tenantId()).isEqualTo(TENANT);
        assertThat(license.entitlements().plan()).isEqualTo("pro");
        assertThat(license.entitlements().hasFeature("aiAssistant")).isTrue();
        assertThat(license.entitlements().aiQuotaMonthly()).isEqualTo(500);
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String token = sign(payload("2027-01-01T00:00:00Z"), keys.getPrivate());
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload("2099-01-01T00:00:00Z").getBytes(StandardCharsets.UTF_8));
        String tampered = forgedPayload + token.substring(token.indexOf('.'));

        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(LicenzaNonValidaException.class)
                .hasMessageContaining("Firma");
    }

    @Test
    void rejectsLicenseSignedWithAnotherKey() throws Exception {
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String token = sign(payload("2027-01-01T00:00:00Z"), other.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(LicenzaNonValidaException.class);
    }

    @Test
    void rejectsExpiredLicense() throws Exception {
        String token = sign(payload("2026-01-01T00:00:00Z"), keys.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(LicenzaNonValidaException.class)
                .hasMessageContaining("scaduta");
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> verifier.verify("non-una-licenza")).isInstanceOf(LicenzaNonValidaException.class);
    }
}
