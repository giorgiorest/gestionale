package it.gestionale.api.accesso;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.TenantRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profilo dell'utente corrente: dati anagrafici, tenant, ruoli e permessi. */
@RestController
@RequestMapping("/api/me")
public class ProfiloController {

    private final TenantRepository tenants;
    private final RuoloAutoritaConverter rolesConverter;
    private final RubricaUtentiService directory;

    public ProfiloController(TenantRepository tenants, RuoloAutoritaConverter rolesConverter,
                        RubricaUtentiService directory) {
        this.tenants = tenants;
        this.rolesConverter = rolesConverter;
        this.directory = directory;
    }

    /** Profilo dell'utente; registra anche l'accesso nella rubrica del tenant (destinatari delle notifiche). */
    @GetMapping
    @Transactional
    public UtenteCorrente me(@AuthenticationPrincipal Jwt jwt) {
        List<Ruolo> roles = rolesConverter.roles(jwt).stream().sorted().toList();
        List<String> permissions = roles.stream()
                .flatMap(r -> r.permissions().stream())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(Permesso::code)
                .toList();

        UtenteCorrente.InfoTenant tenant = tenants.findById(ContestoTenant.require())
                .map(t -> new UtenteCorrente.InfoTenant(t.getId(), t.getName(),
                        t.getKind().name().toLowerCase(Locale.ROOT), t.getStatus().name().toLowerCase(Locale.ROOT)))
                .orElse(null);
        if (tenant != null) {
            directory.sync(jwt);
        }

        return new UtenteCorrente(jwt.getSubject(), jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("email"), tenant,
                roles.stream().map(Ruolo::code).toList(), permissions);
    }
}
