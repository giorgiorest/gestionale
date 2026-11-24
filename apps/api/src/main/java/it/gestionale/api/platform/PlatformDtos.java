package it.gestionale.api.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.platform.identity.TenantUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dati scambiati con la console di piattaforma. */
public final class PlatformDtos {

    private PlatformDtos() {
    }

    /** Sigla dell'azienda: minuscole, cifre e trattini. */
    static final String SLUG = "^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$";
    /** Nome utente come lo accetta il provider di identità (senza spazi). */
    static final String USERNAME = "^[a-zA-Z0-9._@-]{3,60}$";

    public record AdminDto(String id, String name, String email) {
    }

    public record LicenseDto(String plan, int maxUsers, Set<String> features, int aiQuotaMonthly,
                             Instant validFrom, Instant validUntil) {
    }

    public record TenantDto(UUID id, String name, String slug, String kind, String status, Instant createdAt,
                            LicenseDto license) {
    }

    public record LicenseInput(@NotBlank @Size(max = 40) String plan,
                               @Min(1) @Max(100000) int maxUsers,
                               @NotNull Set<@NotBlank String> features,
                               @Min(0) @Max(10_000_000) int aiQuotaMonthly,
                               Instant validUntil) {
    }

    public record NewTenantInput(@NotNull UUID id,
                                 @NotBlank @Size(max = 200) String name,
                                 @NotBlank @Pattern(regexp = SLUG) String slug,
                                 @NotNull @Valid LicenseInput license) {
    }

    public record TenantUpdate(@NotBlank @Size(max = 200) String name,
                               @NotBlank @Pattern(regexp = "active|suspended") String status) {
    }

    public record NewUserInput(@NotBlank @Pattern(regexp = USERNAME) String username,
                               @NotBlank @Email @Size(max = 255) String email,
                               @NotBlank @Size(max = 100) String firstName,
                               @NotBlank @Size(max = 100) String lastName,
                               @NotBlank @Pattern(regexp = "admin|operator|viewer") String role) {
    }

    public record UserUpdate(Boolean enabled, @Pattern(regexp = "admin|operator|viewer") String role) {
    }

    /** Risposta di creazione o reset: la password provvisoria si mostra una sola volta e non viene salvata. */
    public record UserWithPassword(TenantUser user, String temporaryPassword) {
    }

    public record PasswordReset(String temporaryPassword) {
    }

    public record AuditDto(UUID id, String actorName, String action, Map<String, Object> details, Instant createdAt) {
    }

    public record Catalog(List<String> features, List<String> roles) {
    }
}
