package it.gestionale.api.lavorazioni;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lavorazioni delle commesse (classificano le ore nel rapportino), del tenant corrente. */
@RestController
public class LavorazioneController {

    private final LavorazioneService service;

    public LavorazioneController(LavorazioneService service) {
        this.service = service;
    }

    @GetMapping("/api/commesse/{commessaId}/work-activities")
    @PreAuthorize("hasAuthority('work-activities:read')")
    public List<LavorazioneDto> listByCommessa(@PathVariable UUID commessaId) {
        return service.listByCommessa(commessaId);
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/api/work-activities/{id}")
    @PreAuthorize("hasAuthority('work-activities:write')")
    public LavorazioneDto upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody LavorazioneDto.Input input) {
        return service.upsert(id, input, jwt.getSubject());
    }

    /**
     * Cancellazione logica, idempotente: risponde 204 anche se la lavorazione non esiste o era
     * già cancellata, così una voce della outbox non resta bloccata in coda per sempre.
     */
    @DeleteMapping("/api/work-activities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('work-activities:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
