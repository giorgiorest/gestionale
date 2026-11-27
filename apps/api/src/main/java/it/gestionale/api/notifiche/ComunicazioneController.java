package it.gestionale.api.notifiche;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import it.gestionale.api.accesso.RubricaUtentiService;
import it.gestionale.api.accesso.UtenteRubrica;
import it.gestionale.api.notifiche.NotificaDtos.ComunicazioneInput;
import it.gestionale.api.notifiche.NotificaDtos.RiepilogoComunicazione;
import it.gestionale.api.notifiche.NotificaDtos.StatoDestinatario;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Comunicazioni ai dipendenti: invio, elenco, report della presa visione. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('notifications:send')")
public class ComunicazioneController {

    private final NotificaService notifications;
    private final RubricaUtentiService directory;

    public ComunicazioneController(NotificaService notifications, RubricaUtentiService directory) {
        this.notifications = notifications;
        this.directory = directory;
    }

    /** Invio idempotente: l'id è generato dal client (UUID v7) e la richiesta arriva dalla outbox. */
    @PutMapping("/announcements/{id}")
    public RiepilogoComunicazione send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody ComunicazioneInput input) {
        String senderName = jwt.getClaimAsString("name");
        notifications.send(new NuovaNotifica(id, NuovaNotifica.ANNOUNCEMENT, "announcement",
                input.title().trim(), input.body() == null ? "" : input.body().trim(), input.link(),
                new Pubblico(input.audience().type(), blankToNull(input.audience().value())),
                input.requiresAck(), input.sendEmail(), jwt.getSubject(),
                senderName != null ? senderName : jwt.getClaimAsString("preferred_username")));
        return notifications.getAnnouncement(id);
    }

    @GetMapping("/announcements")
    public List<RiepilogoComunicazione> list(@RequestParam(defaultValue = "100") int limit) {
        return notifications.listAnnouncements(Math.clamp(limit, 1, 500));
    }

    @GetMapping("/announcements/{id}")
    public RiepilogoComunicazione get(@PathVariable UUID id) {
        return notifications.getAnnouncement(id);
    }

    /** Chi ha letto e chi ha confermato la presa visione. */
    @GetMapping("/announcements/{id}/recipients")
    public List<StatoDestinatario> recipients(@PathVariable UUID id) {
        return notifications.recipients(id);
    }

    /** Rubrica degli utenti del tenant, per scegliere il destinatario. */
    @GetMapping("/directory/users")
    public List<UtenteRubrica> users() {
        return directory.list();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
