package it.gestionale.api.dailyreports;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rapportini giornalieri (solo testata, DRAFT) delle commesse del tenant corrente. */
@RestController
public class DailyReportController {

    private final DailyReportService service;

    public DailyReportController(DailyReportService service) {
        this.service = service;
    }

    /** Senza {@code date}, tutti i rapportini della commessa; con {@code date}, al più uno. */
    @GetMapping("/api/commesse/{commessaId}/daily-reports")
    @PreAuthorize("hasAuthority('daily-reports:read')")
    public List<DailyReportDto> listByCommessa(@PathVariable UUID commessaId,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.listByCommessa(commessaId, date);
    }

    @GetMapping("/api/daily-reports/{id}")
    @PreAuthorize("hasAuthority('daily-reports:read')")
    public DailyReportDto get(@PathVariable UUID id) {
        return service.get(id).orElseThrow(DailyReportNotFoundException::new);
    }

    /** Creazione o aggiornamento idempotente: l'id è generato dal client (UUID v7). */
    @PutMapping("/api/daily-reports/{id}")
    @PreAuthorize("hasAuthority('daily-reports:write')")
    public DailyReportDto upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody DailyReportDto.Input input) {
        return service.upsert(id, input, jwt.getSubject());
    }

    /**
     * Cancellazione logica, idempotente: risponde 204 anche se il rapportino non esiste o era
     * già cancellato, così una voce della outbox non resta bloccata in coda per sempre.
     */
    @DeleteMapping("/api/daily-reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('daily-reports:write')")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }
}
