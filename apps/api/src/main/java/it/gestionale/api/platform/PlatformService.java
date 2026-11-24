package it.gestionale.api.platform;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gestionale.api.licensing.Features;
import it.gestionale.api.licensing.License;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.platform.PlatformDtos.AuditDto;
import it.gestionale.api.platform.PlatformDtos.LicenseDto;
import it.gestionale.api.platform.PlatformDtos.LicenseInput;
import it.gestionale.api.platform.PlatformDtos.NewTenantInput;
import it.gestionale.api.platform.PlatformDtos.NewUserInput;
import it.gestionale.api.platform.PlatformDtos.TenantDto;
import it.gestionale.api.platform.PlatformDtos.TenantUpdate;
import it.gestionale.api.platform.PlatformDtos.UserUpdate;
import it.gestionale.api.platform.PlatformDtos.UserWithPassword;
import it.gestionale.api.platform.identity.IdentityAdmin;
import it.gestionale.api.platform.identity.NewTenantUser;
import it.gestionale.api.platform.identity.TenantUser;
import it.gestionale.api.tenancy.Tenant;
import it.gestionale.api.tenancy.TenantAccessService;
import it.gestionale.api.tenancy.TenantKind;
import it.gestionale.api.tenancy.TenantRepository;
import it.gestionale.api.tenancy.TenantStatus;
import it.gestionale.api.tenancy.WorkerContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Operazioni della console di piattaforma: aziende, licenze, utenti e registro.
 *
 * <p>Tutti gli accessi al database avvengono con {@code app.worker = 'platform-admin'}
 * ({@link WorkerContext}): le policy RLS dedicate (V5) mostrano SOLO aziende, licenze e registro
 * di tutte le aziende; i dati di dominio restano invisibili. Ogni modifica viene registrata in
 * {@code platform_audit} con l'amministratore che l'ha fatta (mai password o segreti).
 */
@Service
public class PlatformService {

    static final String WORKER = "platform-admin";

    private final TenantRepository tenants;
    private final LicenseRepository licenses;
    private final IdentityAdmin identity;
    private final TenantAccessService tenantAccess;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;

    public PlatformService(TenantRepository tenants, LicenseRepository licenses, IdentityAdmin identity,
                           TenantAccessService tenantAccess, JdbcTemplate jdbc, TransactionTemplate tx,
                           ObjectMapper json, Clock clock) {
        this.tenants = tenants;
        this.licenses = licenses;
        this.identity = identity;
        this.tenantAccess = tenantAccess;
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    // --- Aziende e licenze ------------------------------------------------------------------------

    public List<TenantDto> listTenants() {
        return asPlatform(() -> {
            Map<UUID, License> latest = licenses.findAll().stream()
                    .collect(Collectors.toMap(License::getTenantId, Function.identity(),
                            (a, b) -> a.getValidFrom().isAfter(b.getValidFrom()) ? a : b));
            return tenants.findAll().stream()
                    .sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(t -> toDto(t, latest.get(t.getId())))
                    .toList();
        });
    }

    public TenantDto getTenant(UUID id) {
        return asPlatform(() -> toDto(requireTenant(id), currentLicense(id)));
    }

    public TenantDto createTenant(NewTenantInput input, PlatformAdmin admin) {
        validateFeatures(input.license().features());
        return asPlatform(() -> {
            if (tenants.existsById(input.id())) {
                throw new PlatformException(HttpStatus.CONFLICT, "Esiste già un'azienda con questo identificativo");
            }
            if (tenants.findAll().stream().anyMatch(t -> t.getSlug().equals(input.slug()))) {
                throw new PlatformException(HttpStatus.CONFLICT, "Sigla già usata da un'altra azienda");
            }
            Tenant tenant = tenants.save(new Tenant(input.id(), input.name().trim(), input.slug(), TenantKind.CUSTOMER));
            LicenseInput l = input.license();
            Instant now = clock.instant();
            validateValidity(now, l.validUntil());
            License license = licenses.save(new License(UUID.randomUUID(), tenant.getId(), l.plan().trim(), l.maxUsers(),
                    l.features(), l.aiQuotaMonthly(), now, l.validUntil()));
            tenants.flush();
            licenses.flush();
            audit(tenant.getId(), admin, "tenant.create", Map.of("name", tenant.getName(), "slug", tenant.getSlug(),
                    "license", licenseDetails(license)));
            return toDto(tenant, license);
        });
    }

    public TenantDto updateTenant(UUID id, TenantUpdate update, PlatformAdmin admin) {
        TenantDto result = asPlatform(() -> {
            Tenant tenant = requireTenant(id);
            Map<String, Object> details = new LinkedHashMap<>();
            if (!tenant.getName().equals(update.name().trim())) {
                details.put("name", Map.of("from", tenant.getName(), "to", update.name().trim()));
                tenant.rename(update.name().trim());
            }
            TenantStatus status = "suspended".equals(update.status()) ? TenantStatus.SUSPENDED : TenantStatus.ACTIVE;
            String action = "tenant.update";
            if (tenant.getStatus() != status) {
                details.put("status", Map.of("from", code(tenant.getStatus()), "to", code(status)));
                action = status == TenantStatus.SUSPENDED ? "tenant.suspend" : "tenant.activate";
                tenant.setStatus(status);
            }
            tenants.saveAndFlush(tenant);
            if (!details.isEmpty()) {
                audit(id, admin, action, details);
            }
            return toDto(tenant, currentLicense(id));
        });
        tenantAccess.evict(id);
        return result;
    }

    public TenantDto updateLicense(UUID id, LicenseInput input, PlatformAdmin admin) {
        validateFeatures(input.features());
        return asPlatform(() -> {
            Tenant tenant = requireTenant(id);
            License license = currentLicense(id);
            Instant now = clock.instant();
            if (license == null) {
                validateValidity(now, input.validUntil());
                license = new License(UUID.randomUUID(), id, input.plan().trim(), input.maxUsers(), input.features(),
                        input.aiQuotaMonthly(), now, input.validUntil());
            } else {
                if (license.getSignature() != null) {
                    throw new PlatformException(HttpStatus.CONFLICT, "Licenza offline firmata: si sostituisce con una nuova licenza, non si modifica");
                }
                validateValidity(license.getValidFrom(), input.validUntil());
                license.update(input.plan().trim(), input.maxUsers(), input.features(), input.aiQuotaMonthly(),
                        input.validUntil());
            }
            licenses.saveAndFlush(license);
            audit(id, admin, "license.update", licenseDetails(license));
            return toDto(tenant, license);
        });
    }

    // --- Utenti -----------------------------------------------------------------------------------

    public List<TenantUser> listUsers(UUID tenantId) {
        asPlatform(() -> requireTenant(tenantId));
        return identity.listUsers(tenantId);
    }

    public UserWithPassword createUser(UUID tenantId, NewUserInput input, PlatformAdmin admin) {
        License license = asPlatform(() -> {
            requireTenant(tenantId);
            return currentLicense(tenantId);
        });
        int maxUsers = license == null ? 0 : license.getMaxUsers();
        if (identity.countUsers(tenantId) >= maxUsers) {
            throw new PlatformException(HttpStatus.CONFLICT,
                    "Raggiunto il numero massimo di utenti della licenza (" + maxUsers + "): aumentalo o disattiva un utente");
        }
        String password = TemporaryPasswords.generate();
        TenantUser user = identity.createUser(tenantId, new NewTenantUser(input.username().trim(),
                input.email().trim(), input.firstName().trim(), input.lastName().trim(), input.role()), password);
        asPlatform(() -> {
            audit(tenantId, admin, "user.create", Map.of("userId", user.id(), "username", user.username(),
                    "role", input.role()));
            return null;
        });
        return new UserWithPassword(user, password);
    }

    public TenantUser updateUser(UUID tenantId, String userId, UserUpdate update, PlatformAdmin admin) {
        asPlatform(() -> requireTenant(tenantId));
        TenantUser user = identity.updateUser(tenantId, userId, update.enabled(), update.role());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("userId", user.id());
        details.put("username", user.username());
        if (update.enabled() != null) {
            details.put("enabled", update.enabled());
        }
        if (update.role() != null) {
            details.put("role", update.role());
        }
        String action = update.enabled() == null ? "user.role" : update.enabled() ? "user.enable" : "user.disable";
        asPlatform(() -> {
            audit(tenantId, admin, action, details);
            return null;
        });
        return user;
    }

    public String resetPassword(UUID tenantId, String userId, PlatformAdmin admin) {
        asPlatform(() -> requireTenant(tenantId));
        String password = TemporaryPasswords.generate();
        identity.resetPassword(tenantId, userId, password);
        asPlatform(() -> {
            audit(tenantId, admin, "user.password-reset", Map.of("userId", userId));
            return null;
        });
        return password;
    }

    // --- Registro ---------------------------------------------------------------------------------

    public List<AuditDto> audit(UUID tenantId) {
        return asPlatform(() -> {
            requireTenant(tenantId);
            return jdbc.query("""
                    SELECT id, actor_name, action, details::text AS details, created_at
                    FROM platform_audit WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 200
                    """, (rs, i) -> new AuditDto(rs.getObject("id", UUID.class), rs.getString("actor_name"),
                    rs.getString("action"), parse(rs.getString("details")),
                    rs.getTimestamp("created_at").toInstant()), tenantId);
        });
    }

    // --- Dettagli ---------------------------------------------------------------------------------

    private <T> T asPlatform(java.util.function.Supplier<T> work) {
        return WorkerContext.callAs(WORKER, () -> tx.execute(status -> work.get()));
    }

    private Tenant requireTenant(UUID id) {
        return tenants.findById(id).orElseThrow(PlatformException::tenantNotFound);
    }

    private License currentLicense(UUID tenantId) {
        return licenses.findByTenantIdOrderByValidFromDesc(tenantId).stream().findFirst().orElse(null);
    }

    private void audit(UUID tenantId, PlatformAdmin admin, String action, Map<String, Object> details) {
        try {
            jdbc.update("""
                    INSERT INTO platform_audit (id, tenant_id, actor_id, actor_name, action, details)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb)
                    """, UUID.randomUUID(), tenantId, admin.id(), admin.name(), action, json.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> parse(String details) {
        try {
            return json.readValue(details, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static void validateFeatures(Set<String> features) {
        for (String feature : features) {
            if (!Features.ALL.contains(feature)) {
                throw new PlatformException(HttpStatus.BAD_REQUEST, "Funzionalità sconosciuta: " + feature);
            }
        }
    }

    private static void validateValidity(Instant validFrom, Instant validUntil) {
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new PlatformException(HttpStatus.BAD_REQUEST, "La scadenza deve essere successiva all'inizio della licenza");
        }
    }

    private static Map<String, Object> licenseDetails(License l) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("plan", l.getPlan());
        details.put("maxUsers", l.getMaxUsers());
        details.put("features", l.getFeatures().stream().sorted().toList());
        details.put("aiQuotaMonthly", l.getAiQuotaMonthly());
        details.put("validUntil", l.getValidUntil() == null ? null : l.getValidUntil().toString());
        return details;
    }

    private static String code(TenantStatus status) {
        return status.name().toLowerCase();
    }

    private static TenantDto toDto(Tenant t, License l) {
        LicenseDto license = l == null ? null : new LicenseDto(l.getPlan(), l.getMaxUsers(),
                l.getFeatures(), l.getAiQuotaMonthly(), l.getValidFrom(), l.getValidUntil());
        return new TenantDto(t.getId(), t.getName(), t.getSlug(), t.getKind().name().toLowerCase(),
                code(t.getStatus()), t.getCreatedAt(), license);
    }
}
