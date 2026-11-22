package it.gestionale.api.tenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param tenantClaim nome del claim JWT con l'id (UUID) del tenant
 */
@ConfigurationProperties("gestionale.tenancy")
public record TenancyProperties(@DefaultValue("tenant_id") String tenantClaim) {
}
