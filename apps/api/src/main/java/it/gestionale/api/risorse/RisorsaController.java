package it.gestionale.api.risorse;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Risorse (dipendenti che compilano i rapportini) del tenant corrente. */
@RestController
@RequestMapping("/api/resources")
public class RisorsaController {

    private final RisorsaService service;

    public RisorsaController(RisorsaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('resources:read')")
    public List<RisorsaDto> list() {
        return service.list();
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('resources:write')")
    public RisorsaDto upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody RisorsaDto.Input input) {
        return service.upsert(id, input, jwt.getSubject());
    }

    /**
     * Tariffa oraria base (#53): permesso dedicato {@code labor-cost:read/write}, non
     * {@code resources:read/write} — vedi ADR 0021.
     */
    @GetMapping("/{id}/labor-cost")
    @PreAuthorize("hasAuthority('labor-cost:read')")
    public CostoManodoperaRisorsaDto laborCost(@PathVariable UUID id) {
        return service.getLaborCost(id);
    }

    @PutMapping("/{id}/labor-cost")
    @PreAuthorize("hasAuthority('labor-cost:write')")
    public CostoManodoperaRisorsaDto updateLaborCost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @Valid @RequestBody CostoManodoperaRisorsaDto.Input input) {
        return service.updateLaborCost(id, input, jwt.getSubject());
    }

    /**
     * Cancellazione logica, idempotente: risponde 204 anche se la risorsa non esiste o era già
     * cancellata, così una voce della outbox non resta bloccata in coda per sempre.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('resources:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
