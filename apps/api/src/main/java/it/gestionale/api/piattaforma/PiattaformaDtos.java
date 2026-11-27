package it.gestionale.api.piattaforma;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.gestionale.api.piattaforma.identita.UtenteTenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dati scambiati con la console di piattaforma. */
public final class PiattaformaDtos {

    private PiattaformaDtos() {
    }

    /** Sigla dell'azienda: minuscole, cifre e trattini. */
    static final String SLUG = "^[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?$";
    /** Nome utente come lo accetta il provider di identità (senza spazi). */
    static final String USERNAME = "^[a-zA-Z0-9._@-]{3,60}$";

    public record AmministratoreDto(String id, String name, String email) {
    }

    public record LicenzaDto(String plan, int maxUsers, Set<String> features, int aiQuotaMonthly,
                             Instant validFrom, Instant validUntil) {
    }

    public record TenantDto(UUID id, String name, String slug, String kind, String status, Instant createdAt,
                            LicenzaDto license) {
    }

    public record LicenzaInput(@NotBlank @Size(max = 40) String plan,
                               @Min(1) @Max(100000) int maxUsers,
                               @NotNull Set<@NotBlank String> features,
                               @Min(0) @Max(10_000_000) int aiQuotaMonthly,
                               Instant validUntil) {
    }

    public record NuovoTenantInput(@NotNull UUID id,
                                 @NotBlank @Size(max = 200) String name,
                                 @NotBlank @Pattern(regexp = SLUG) String slug,
                                 @NotNull @Valid LicenzaInput license) {
    }

    public record AggiornamentoTenant(@NotBlank @Size(max = 200) String name,
                               @NotBlank @Pattern(regexp = "active|suspended") String status) {
    }

    /** {@code role}: un codice valido per {@link it.gestionale.api.accesso.Ruolo}, verificato in {@code PiattaformaService}. */
    public record NuovoUtenteInput(@NotBlank @Pattern(regexp = USERNAME) String username,
                               @NotBlank @Email @Size(max = 255) String email,
                               @NotBlank @Size(max = 100) String firstName,
                               @NotBlank @Size(max = 100) String lastName,
                               @NotBlank String role) {
    }

    /** {@code role}, se presente: un codice valido per {@link it.gestionale.api.accesso.Ruolo}, verificato in {@code PiattaformaService}. */
    public record AggiornamentoUtente(Boolean enabled, String role) {
    }

    /** Risposta di creazione o reset: la password provvisoria si mostra una sola volta e non viene salvata. */
    public record UtenteConPassword(UtenteTenant user, String temporaryPassword) {
    }

    public record ReimpostaPassword(String temporaryPassword) {
    }

    public record AuditDto(UUID id, String actorName, String action, Map<String, Object> details, Instant createdAt) {
    }

    public record Catalog(List<String> features, List<String> roles) {
    }
}
