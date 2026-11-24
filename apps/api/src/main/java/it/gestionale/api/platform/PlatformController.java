package it.gestionale.api.platform;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.auth.Role;
import it.gestionale.api.licensing.Features;
import it.gestionale.api.platform.PlatformDtos.AdminDto;
import it.gestionale.api.platform.PlatformDtos.AuditDto;
import it.gestionale.api.platform.PlatformDtos.Catalog;
import it.gestionale.api.platform.PlatformDtos.LicenseInput;
import it.gestionale.api.platform.PlatformDtos.NewTenantInput;
import it.gestionale.api.platform.PlatformDtos.NewUserInput;
import it.gestionale.api.platform.PlatformDtos.PasswordReset;
import it.gestionale.api.platform.PlatformDtos.TenantDto;
import it.gestionale.api.platform.PlatformDtos.TenantUpdate;
import it.gestionale.api.platform.PlatformDtos.UserUpdate;
import it.gestionale.api.platform.PlatformDtos.UserWithPassword;
import it.gestionale.api.platform.identity.TenantUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API della console di piattaforma. Raggiungibile solo con un token del realm degli
 * amministratori ({@link PlatformSecurityConfig}); ogni metodo ripete il controllo.
 */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasAuthority('" + PlatformSecurityConfig.PLATFORM_ADMIN + "')")
public class PlatformController {

    private final PlatformService service;

    public PlatformController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public AdminDto me(@AuthenticationPrincipal Jwt jwt) {
        PlatformAdmin admin = PlatformAdmin.from(jwt);
        return new AdminDto(admin.id(), admin.name(), admin.email());
    }

    /** Funzionalità e ruoli tra cui scegliere nella console. */
    @GetMapping("/catalog")
    public Catalog catalog() {
        return new Catalog(Features.ALL.stream().sorted().toList(),
                Arrays.stream(Role.values()).map(Role::code).toList());
    }

    @GetMapping("/tenants")
    public List<TenantDto> tenants() {
        return service.listTenants();
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDto createTenant(@Valid @RequestBody NewTenantInput input, @AuthenticationPrincipal Jwt jwt) {
        return service.createTenant(input, PlatformAdmin.from(jwt));
    }

    @GetMapping("/tenants/{id}")
    public TenantDto tenant(@PathVariable UUID id) {
        return service.getTenant(id);
    }

    @PutMapping("/tenants/{id}")
    public TenantDto updateTenant(@PathVariable UUID id, @Valid @RequestBody TenantUpdate update,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.updateTenant(id, update, PlatformAdmin.from(jwt));
    }

    @PutMapping("/tenants/{id}/license")
    public TenantDto updateLicense(@PathVariable UUID id, @Valid @RequestBody LicenseInput input,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.updateLicense(id, input, PlatformAdmin.from(jwt));
    }

    @GetMapping("/tenants/{id}/users")
    public List<TenantUser> users(@PathVariable UUID id) {
        return service.listUsers(id);
    }

    @PostMapping("/tenants/{id}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserWithPassword createUser(@PathVariable UUID id, @Valid @RequestBody NewUserInput input,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.createUser(id, input, PlatformAdmin.from(jwt));
    }

    @PutMapping("/tenants/{id}/users/{userId}")
    public TenantUser updateUser(@PathVariable UUID id, @PathVariable String userId,
                                 @Valid @RequestBody UserUpdate update, @AuthenticationPrincipal Jwt jwt) {
        return service.updateUser(id, userId, update, PlatformAdmin.from(jwt));
    }

    @PostMapping("/tenants/{id}/users/{userId}/password")
    public PasswordReset resetPassword(@PathVariable UUID id, @PathVariable String userId,
                                       @AuthenticationPrincipal Jwt jwt) {
        return new PasswordReset(service.resetPassword(id, userId, PlatformAdmin.from(jwt)));
    }

    @GetMapping("/tenants/{id}/audit")
    public List<AuditDto> audit(@PathVariable UUID id) {
        return service.audit(id);
    }
}
