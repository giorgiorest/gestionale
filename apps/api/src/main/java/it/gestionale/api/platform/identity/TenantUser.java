package it.gestionale.api.platform.identity;

import java.time.Instant;

/** Utente di un'azienda come lo vede la console (nessuna credenziale). */
public record TenantUser(String id, String username, String email, String firstName, String lastName,
                         boolean enabled, String role, Instant createdAt) {
}
