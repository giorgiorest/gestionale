package it.gestionale.api.notifications;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import it.gestionale.api.notifications.NotificationDtos.AnnouncementInput;
import it.gestionale.api.notifications.NotificationDtos.AnnouncementSummary;
import it.gestionale.api.notifications.NotificationDtos.DirectoryUser;
import it.gestionale.api.notifications.NotificationDtos.RecipientStatus;
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
public class AnnouncementController {

    private final NotificationService notifications;
    private final UserDirectoryService directory;

    public AnnouncementController(NotificationService notifications, UserDirectoryService directory) {
        this.notifications = notifications;
        this.directory = directory;
    }

    /** Invio idempotente: l'id è generato dal client (UUID v7) e la richiesta arriva dalla outbox. */
    @PutMapping("/announcements/{id}")
    public AnnouncementSummary send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody AnnouncementInput input) {
        String senderName = jwt.getClaimAsString("name");
        notifications.send(new NewNotification(id, NewNotification.ANNOUNCEMENT, "announcement",
                input.title().trim(), input.body() == null ? "" : input.body().trim(), input.link(),
                new Audience(input.audience().type(), blankToNull(input.audience().value())),
                input.requiresAck(), input.sendEmail(), jwt.getSubject(),
                senderName != null ? senderName : jwt.getClaimAsString("preferred_username")));
        return notifications.getAnnouncement(id);
    }

    @GetMapping("/announcements")
    public List<AnnouncementSummary> list(@RequestParam(defaultValue = "100") int limit) {
        return notifications.listAnnouncements(Math.clamp(limit, 1, 500));
    }

    @GetMapping("/announcements/{id}")
    public AnnouncementSummary get(@PathVariable UUID id) {
        return notifications.getAnnouncement(id);
    }

    /** Chi ha letto e chi ha confermato la presa visione. */
    @GetMapping("/announcements/{id}/recipients")
    public List<RecipientStatus> recipients(@PathVariable UUID id) {
        return notifications.recipients(id);
    }

    /** Rubrica degli utenti del tenant, per scegliere il destinatario. */
    @GetMapping("/directory/users")
    public List<DirectoryUser> users() {
        return directory.list();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
