package it.gestionale.api.auth;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profilo dell'utente corrente: dati anagrafici, tenant, ruoli e permessi. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final TenantRepository tenants;
    private final RoleAuthoritiesConverter rolesConverter;

    public MeController(TenantRepository tenants, RoleAuthoritiesConverter rolesConverter) {
        this.tenants = tenants;
        this.rolesConverter = rolesConverter;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
        List<Role> roles = rolesConverter.roles(jwt).stream().sorted().toList();
        List<String> permissions = roles.stream()
                .flatMap(r -> r.permissions().stream())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(Permission::code)
                .toList();

        CurrentUser.TenantInfo tenant = tenants.findById(TenantContext.require())
                .map(t -> new CurrentUser.TenantInfo(t.getId(), t.getName(),
                        t.getKind().name().toLowerCase(Locale.ROOT), t.getStatus().name().toLowerCase(Locale.ROOT)))
                .orElse(null);

        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("email"), tenant,
                roles.stream().map(Role::code).toList(), permissions);
    }
}
