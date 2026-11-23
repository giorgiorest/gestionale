package it.gestionale.api.auth;

import java.util.List;
import java.util.UUID;

/**
 * Profilo dell'utente autenticato, restituito da {@code GET /api/me}.
 *
 * @param userId      identificativo stabile dell'utente nel provider (claim {@code sub})
 * @param roles       ruoli applicativi
 * @param permissions permessi derivati dai ruoli
 */
public record CurrentUser(String userId, String username, String name, String email, TenantInfo tenant,
                          List<String> roles, List<String> permissions) {

    public record TenantInfo(UUID id, String name, String kind, String status) {
    }
}
