package it.gestionale.api.piattaforma;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Console di amministrazione della piattaforma (ADR 0010).
 *
 * @param jwt      emittente dei token degli amministratori (realm separato, es. "piattaforma")
 * @param keycloak accesso di servizio al provider di identità per gestire gli utenti delle aziende
 */
@ConfigurationProperties("gestionale.platform")
public record PiattaformaProperties(@DefaultValue Jwt jwt, @DefaultValue Keycloak keycloak) {

    /**
     * @param issuerUri issuer pubblico del realm degli amministratori; vuoto = console disattivata (401)
     * @param jwkSetUri chiavi di firma lette dalla rete interna (facoltativo; altrimenti dall'issuer)
     * @param audience  audience richiesta nel token
     * @param adminRole ruolo del realm che concede l'accesso alla console
     */
    public record Jwt(String issuerUri, String jwkSetUri,
                      @DefaultValue("gestionale-platform") String audience,
                      @DefaultValue("platform-admin") String adminRole) {
    }

    /**
     * @param baseUrl      indirizzo interno di Keycloak (es. http://keycloak:8080/sso)
     * @param realm        realm degli utenti delle aziende
     * @param clientId     client riservato con service account (manage-users)
     * @param clientSecret segreto del client: solo da variabile d'ambiente
     */
    public record Keycloak(String baseUrl,
                           @DefaultValue("gestionale") String realm,
                           @DefaultValue("gestionale-platform-admin") String clientId,
                           String clientSecret) {
    }
}
