package it.gestionale.api.resourceassignments;

import java.util.List;
import java.util.UUID;

import it.gestionale.api.auth.Permission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Assegnazioni di risorse alle commesse (tab "Risorse" della commessa), del tenant corrente. */
@RestController
public class ResourceAssignmentController {

    private final ResourceAssignmentService service;

    public ResourceAssignmentController(ResourceAssignmentService service) {
        this.service = service;
    }

    /**
     * {@code hourlyCostOverride} è un costo (contestuale alla commessa) tanto quanto la tariffa
     * oraria base di {@code Resource}: va nascosto a chi ha {@code resource-assignments:read} ma
     * non {@code labor-cost:read}, per coerenza con quella (vedi ADR 0021, nota sulla falla ora
     * risolta).
     */
    @GetMapping("/api/commesse/{commessaId}/resource-assignments")
    @PreAuthorize("hasAuthority('resource-assignments:read')")
    public List<ResourceAssignmentDto> listByCommessa(@PathVariable UUID commessaId) {
        List<ResourceAssignmentDto> assignments = service.listByCommessa(commessaId);
        return hasLaborCostRead() ? assignments
                : assignments.stream().map(ResourceAssignmentDto::withoutHourlyCostOverride).toList();
    }

    private static boolean hasLaborCostRead() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Permission.LABOR_COST_READ.code()));
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/api/resource-assignments/{id}")
    @PreAuthorize("hasAuthority('resource-assignments:write')")
    public ResourceAssignmentDto upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody ResourceAssignmentDto.Input input) {
        return service.upsert(id, input, jwt.getSubject());
    }

    /**
     * Cancellazione logica, idempotente: risponde 204 anche se l'assegnazione non esiste o era
     * già cancellata, così una voce della outbox non resta bloccata in coda per sempre.
     */
    @DeleteMapping("/api/resource-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('resource-assignments:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
