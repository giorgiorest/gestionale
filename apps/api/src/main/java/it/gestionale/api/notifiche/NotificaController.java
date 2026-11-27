package it.gestionale.api.notifiche;

import java.util.List;
import java.util.UUID;

import it.gestionale.api.notifiche.NotificaDtos.AggiornamentoNotificaUtente;
import it.gestionale.api.notifiche.NotificaDtos.Counts;
import it.gestionale.api.notifiche.NotificaDtos.NotificaUtente;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notifiche dell'utente autenticato: ognuno vede e aggiorna solo le proprie. */
@RestController
@RequestMapping("/api/notifications")
public class NotificaController {

    private final NotificaService service;

    public NotificaController(NotificaService service) {
        this.service = service;
    }

    /** @param open se true solo le non lette o ancora da confermare */
    @GetMapping
    public List<NotificaUtente> list(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "false") boolean open,
                                     @RequestParam(defaultValue = "100") int limit) {
        return service.listMine(jwt.getSubject(), open, Math.clamp(limit, 1, 500));
    }

    @GetMapping("/counts")
    public Counts counts(@AuthenticationPrincipal Jwt jwt) {
        return service.countsMine(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public NotificaUtente get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.getMine(jwt.getSubject(), id);
    }

    /** Segna come letta e/o conferma la presa visione (idempotente, inviato dalla outbox del client). */
    @PutMapping("/{id}")
    public NotificaUtente update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody AggiornamentoNotificaUtente update) {
        return service.updateMine(jwt.getSubject(), id, Boolean.TRUE.equals(update.read()),
                Boolean.TRUE.equals(update.acknowledged()));
    }
}
