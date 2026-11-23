package it.gestionale.api.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Rappresentazioni delle notifiche nell'API. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** Notifica vista dal destinatario. */
    public record MyNotification(UUID id, String kind, String type, String title, String body, String link,
                                 String senderName, Instant createdAt, boolean requiresAck, Instant readAt,
                                 Instant acknowledgedAt) {
    }

    /** Contatori per la campanella. */
    public record Counts(long unread, long toAcknowledge) {
    }

    /** Aggiornamento dello stato da parte del destinatario (idempotente). */
    public record MyUpdate(Boolean read, Boolean acknowledged) {
    }

    /** Comunicazione inviata da un amministratore. L'id è generato dal client. */
    public record AnnouncementInput(@NotBlank @Size(max = 200) String title,
                                    @Size(max = 10_000) String body,
                                    @Pattern(regexp = "^/[^/].*|^/$", message = "deve essere un percorso dell'app, es. /clienti")
                                    @Size(max = 500) String link,
                                    @NotNull @Valid AudienceInput audience,
                                    boolean requiresAck,
                                    boolean sendEmail) {
    }

    public record AudienceInput(@NotBlank @Pattern(regexp = "user|role|tenant") String type, String value) {
    }

    /** Comunicazione con le statistiche di lettura e presa visione. */
    public record AnnouncementSummary(UUID id, String title, String body, String link, String audienceType,
                                      String audienceValue, boolean requiresAck, boolean sendEmail,
                                      String senderName, Instant createdAt, long recipients, long read,
                                      long acknowledged, long emailsSent, long emailsFailed) {
    }

    /** Stato di un destinatario (report della presa visione). */
    public record RecipientStatus(String userId, String name, String email, Instant assignedAt, Instant readAt,
                                  Instant acknowledgedAt, String emailStatus) {
    }

    /** Voce della rubrica, per scegliere i destinatari. */
    public record DirectoryUser(String userId, String username, String name, String email, List<String> roles) {
    }
}
