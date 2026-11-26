package it.gestionale.api.suppliers;

import java.util.UUID;

import it.gestionale.api.web.PageDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Anagrafica fornitori del tenant corrente. */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    /** Lista paginata, ordinata e ricercabile (ADR 0014): {@code ?page=&size=&sort=&q=}. */
    @GetMapping
    @PreAuthorize("hasAuthority('suppliers:read')")
    public PageDto<SupplierDto> list(
            @PageableDefault(size = 50, sort = { "name", "id" }, direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String q) {
        return service.list(pageable, q);
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('suppliers:write')")
    public SupplierDto upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody SupplierDto.Input input) {
        return service.upsert(id, input, jwt.getSubject());
    }

    /** Cancellazione logica, idempotente: risponde 204 anche se il fornitore non esiste. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('suppliers:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
