package it.gestionale.api.licensing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicKey chiave pubblica Ed25519 in formato X.509 (SubjectPublicKeyInfo) codificata Base64.
 *                  La chiave privata non sta mai nel repository né nell'app distribuita.
 */
@ConfigurationProperties("gestionale.licensing")
public record LicensingProperties(String publicKey) {
}
