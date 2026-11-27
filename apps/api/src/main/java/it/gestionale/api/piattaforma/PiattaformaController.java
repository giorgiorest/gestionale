package it.gestionale.api.piattaforma;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import it.gestionale.api.accesso.Ruolo;
import it.gestionale.api.licenze.Funzionalita;
import it.gestionale.api.piattaforma.PiattaformaDtos.AggiornamentoTenant;
import it.gestionale.api.piattaforma.PiattaformaDtos.AggiornamentoUtente;
import it.gestionale.api.piattaforma.PiattaformaDtos.AmministratoreDto;
import it.gestionale.api.piattaforma.PiattaformaDtos.AuditDto;
import it.gestionale.api.piattaforma.PiattaformaDtos.Catalog;
import it.gestionale.api.piattaforma.PiattaformaDtos.LicenzaInput;
import it.gestionale.api.piattaforma.PiattaformaDtos.NuovoTenantInput;
import it.gestionale.api.piattaforma.PiattaformaDtos.NuovoUtenteInput;
import it.gestionale.api.piattaforma.PiattaformaDtos.ReimpostaPassword;
import it.gestionale.api.piattaforma.PiattaformaDtos.TenantDto;
import it.gestionale.api.piattaforma.PiattaformaDtos.UtenteConPassword;
import it.gestionale.api.piattaforma.identita.UtenteTenant;
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
 * amministratori ({@link PiattaformaSicurezzaConfig}); ogni metodo ripete il controllo.
 */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasAuthority('" + PiattaformaSicurezzaConfig.PLATFORM_ADMIN + "')")
public class PiattaformaController {

    private final PiattaformaService service;

    public PiattaformaController(PiattaformaService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public AmministratoreDto me(@AuthenticationPrincipal Jwt jwt) {
        AmministratorePiattaforma admin = AmministratorePiattaforma.from(jwt);
        return new AmministratoreDto(admin.id(), admin.name(), admin.email());
    }

    /** Funzionalità e ruoli tra cui scegliere nella console. */
    @GetMapping("/catalog")
    public Catalog catalog() {
        return new Catalog(Funzionalita.ALL.stream().sorted().toList(),
                Arrays.stream(Ruolo.values()).map(Ruolo::code).toList());
    }

    @GetMapping("/tenants")
    public List<TenantDto> tenants() {
        return service.listTenants();
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDto createTenant(@Valid @RequestBody NuovoTenantInput input, @AuthenticationPrincipal Jwt jwt) {
        return service.createTenant(input, AmministratorePiattaforma.from(jwt));
    }

    @GetMapping("/tenants/{id}")
    public TenantDto tenant(@PathVariable UUID id) {
        return service.getTenant(id);
    }

    @PutMapping("/tenants/{id}")
    public TenantDto updateTenant(@PathVariable UUID id, @Valid @RequestBody AggiornamentoTenant update,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.updateTenant(id, update, AmministratorePiattaforma.from(jwt));
    }

    @PutMapping("/tenants/{id}/license")
    public TenantDto updateLicense(@PathVariable UUID id, @Valid @RequestBody LicenzaInput input,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.updateLicense(id, input, AmministratorePiattaforma.from(jwt));
    }

    @GetMapping("/tenants/{id}/users")
    public List<UtenteTenant> users(@PathVariable UUID id) {
        return service.listUsers(id);
    }

    @PostMapping("/tenants/{id}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UtenteConPassword createUser(@PathVariable UUID id, @Valid @RequestBody NuovoUtenteInput input,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.createUser(id, input, AmministratorePiattaforma.from(jwt));
    }

    @PutMapping("/tenants/{id}/users/{userId}")
    public UtenteTenant updateUser(@PathVariable UUID id, @PathVariable String userId,
                                 @Valid @RequestBody AggiornamentoUtente update, @AuthenticationPrincipal Jwt jwt) {
        return service.updateUser(id, userId, update, AmministratorePiattaforma.from(jwt));
    }

    @PostMapping("/tenants/{id}/users/{userId}/password")
    public ReimpostaPassword resetPassword(@PathVariable UUID id, @PathVariable String userId,
                                       @AuthenticationPrincipal Jwt jwt) {
        return new ReimpostaPassword(service.resetPassword(id, userId, AmministratorePiattaforma.from(jwt)));
    }

    @GetMapping("/tenants/{id}/audit")
    public List<AuditDto> audit(@PathVariable UUID id) {
        return service.audit(id);
    }
}
