package it.gestionale.api.workentries;

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

/** Righe di ore dei rapportini giornalieri (#50), del tenant corrente. */
@RestController
public class WorkEntryController {

    private final WorkEntryService service;

    public WorkEntryController(WorkEntryService service) {
        this.service = service;
    }

    @GetMapping("/api/daily-reports/{dailyReportId}/work-entries")
    @PreAuthorize("hasAuthority('work-entries:read')")
    public List<WorkEntryDto> listByDailyReport(@PathVariable UUID dailyReportId) {
        return service.listByDailyReport(dailyReportId);
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/api/work-entries/{id}")
    @PreAuthorize("hasAuthority('work-entries:write')")
    public WorkEntryDto upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody WorkEntryDto.Input input) {
        return service.upsert(id, input, jwt.getSubject());
    }

    /**
     * Cancellazione logica, idempotente: risponde 204 anche se la riga non esiste o era già
     * cancellata, così una voce della outbox non resta bloccata in coda per sempre.
     */
    @DeleteMapping("/api/work-entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('work-entries:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
