package it.gestionale.api.rapportini;

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

/** Righe di materiale dei rapportini giornalieri (voce 3.2 della roadmap), del tenant corrente. */
@RestController
public class MaterialeRapportinoController {

    private final MaterialeRapportinoService service;

    public MaterialeRapportinoController(MaterialeRapportinoService service) {
        this.service = service;
    }

    @GetMapping("/api/daily-reports/{dailyReportId}/materials")
    @PreAuthorize("hasAuthority('daily-report-materials:read')")
    public List<MaterialeRapportinoDto> listByDailyReport(@PathVariable UUID dailyReportId) {
        return service.listByDailyReport(dailyReportId);
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/api/daily-report-materials/{id}")
    @PreAuthorize("hasAuthority('daily-report-materials:write')")
    public MaterialeRapportinoDto upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody MaterialeRapportinoDto.Input input) {
        return service.upsert(id, input, jwt.getSubject());
    }

    /**
     * Cancellazione logica, idempotente: risponde 204 anche se la riga non esiste o era già
     * cancellata, così una voce della outbox non resta bloccata in coda per sempre.
     */
    @DeleteMapping("/api/daily-report-materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('daily-report-materials:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
