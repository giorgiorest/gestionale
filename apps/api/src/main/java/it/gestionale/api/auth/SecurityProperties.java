package it.gestionale.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param rolesClaim nome del claim JWT con l'elenco dei ruoli (claim neutro rispetto al provider)
 */
@ConfigurationProperties("gestionale.security")
public record SecurityProperties(@DefaultValue("roles") String rolesClaim) {
}
