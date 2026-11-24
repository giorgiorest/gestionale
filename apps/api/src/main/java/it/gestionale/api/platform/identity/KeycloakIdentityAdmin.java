package it.gestionale.api.platform.identity;

import static it.gestionale.api.platform.identity.IdentityAdminException.Kind.CONFLICT;
import static it.gestionale.api.platform.identity.IdentityAdminException.Kind.NOT_FOUND;
import static it.gestionale.api.platform.identity.IdentityAdminException.Kind.UNAVAILABLE;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.gestionale.api.auth.Role;
import it.gestionale.api.platform.PlatformProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link IdentityAdmin} con le Admin REST API di Keycloak.
 *
 * <p>Usa il client riservato {@code gestionale-platform-admin} (service account con
 * {@code view-users}, {@code query-users} e {@code manage-users} SOLO sul realm delle aziende, scope
 * {@code kc-admin-api} richiesto da Keycloak 26.8.2+) e il flusso client credentials: nessuna
 * credenziale di amministratore di Keycloak nell'applicazione. Il segreto arriva dal {@code .env}.
 * L'appartenenza all'azienda è l'attributo utente {@code tenant_id}, lo stesso che finisce nel token.
 */
@Component
public class KeycloakIdentityAdmin implements IdentityAdmin {

    private static final String TENANT_ATTRIBUTE = "tenant_id";
    private static final int MAX_USERS = 1000;
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final PlatformProperties.Keycloak config;
    private final RestClient http;
    private final Clock clock;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakIdentityAdmin(PlatformProperties properties, RestClient.Builder builder, Clock clock) {
        this.config = properties.keycloak();
        this.http = builder.build();
        this.clock = clock;
    }

    @Override
    public List<TenantUser> listUsers(UUID tenantId) {
        List<Map<String, Object>> users = call(() -> http.get()
                .uri(admin("/users?briefRepresentation=false&max=" + MAX_USERS + "&q=" + TENANT_ATTRIBUTE + ":" + tenantId))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve().body(LIST));
        List<TenantUser> result = new ArrayList<>();
        for (Map<String, Object> user : users) {
            if (belongsTo(user, tenantId)) {
                result.add(toTenantUser(user, appRole((String) user.get("id"))));
            }
        }
        result.sort(Comparator.comparing(TenantUser::username));
        return result;
    }

    @Override
    public int countUsers(UUID tenantId) {
        Integer count = call(() -> http.get()
                .uri(admin("/users/count?q=" + TENANT_ATTRIBUTE + ":" + tenantId))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve().body(Integer.class));
        return count == null ? 0 : count;
    }

    @Override
    public TenantUser createUser(UUID tenantId, NewTenantUser user, String temporaryPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", user.username());
        body.put("email", user.email());
        body.put("firstName", user.firstName());
        body.put("lastName", user.lastName());
        body.put("enabled", true);
        body.put("emailVerified", false);
        body.put("attributes", Map.of(TENANT_ATTRIBUTE, List.of(tenantId.toString())));
        body.put("credentials", List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", true)));

        URI location = call(() -> http.post().uri(admin("/users"))
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity().getHeaders().getLocation());
        if (location == null) {
            throw new IdentityAdminException(UNAVAILABLE, "Il provider di identità non ha restituito l'utente creato");
        }
        String path = location.getPath();
        String userId = path.substring(path.lastIndexOf('/') + 1);
        try {
            setAppRole(userId, user.role());
        } catch (RuntimeException e) {
            // Niente utenti a metà: senza ruolo l'utente viene eliminato.
            call(() -> http.delete().uri(admin("/users/" + userId)).headers(h -> h.setBearerAuth(token()))
                    .retrieve().toBodilessEntity());
            throw e;
        }
        return toTenantUser(fetchUser(tenantId, userId), user.role());
    }

    @Override
    public TenantUser updateUser(UUID tenantId, String userId, Boolean enabled, String role) {
        Map<String, Object> user = fetchUser(tenantId, userId);
        if (enabled != null && !enabled.equals(user.get("enabled"))) {
            user.put("enabled", enabled);
            call(() -> http.put().uri(admin("/users/" + userId))
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(user)
                    .retrieve().toBodilessEntity());
            if (!enabled) {
                logout(userId);
            }
        }
        if (role != null) {
            setAppRole(userId, role);
        }
        return toTenantUser(fetchUser(tenantId, userId), appRole(userId));
    }

    @Override
    public void resetPassword(UUID tenantId, String userId, String temporaryPassword) {
        fetchUser(tenantId, userId);
        call(() -> http.put().uri(admin("/users/" + userId + "/reset-password"))
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", temporaryPassword, "temporary", true))
                .retrieve().toBodilessEntity());
        logout(userId);
    }

    // --- Dettagli -------------------------------------------------------------------------------

    /** L'utente, solo se appartiene all'azienda; altrimenti "non trovato" (senza rivelare altro). */
    private Map<String, Object> fetchUser(UUID tenantId, String userId) {
        if (userId == null || !userId.matches("[0-9a-fA-F-]{36}")) {
            throw new IdentityAdminException(NOT_FOUND, "Utente non trovato");
        }
        Map<String, Object> user = call(() -> http.get().uri(admin("/users/" + userId))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve().body(MAP));
        if (user == null || !belongsTo(user, tenantId)) {
            throw new IdentityAdminException(NOT_FOUND, "Utente non trovato");
        }
        return new LinkedHashMap<>(user);
    }

    @SuppressWarnings("unchecked")
    private static boolean belongsTo(Map<String, Object> user, UUID tenantId) {
        Object attributes = user.get("attributes");
        if (!(attributes instanceof Map<?, ?> map)) {
            return false;
        }
        Object values = map.get(TENANT_ATTRIBUTE);
        return values instanceof List<?> list && list.size() == 1 && tenantId.toString().equals(list.get(0));
    }

    /** Ruolo applicativo dell'utente (il primo tra admin, operator, viewer), o null. */
    private String appRole(String userId) {
        List<Map<String, Object>> roles = call(() -> http.get().uri(admin("/users/" + userId + "/role-mappings/realm"))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve().body(LIST));
        return roles == null ? null : roles.stream()
                .map(r -> (String) r.get("name"))
                .filter(name -> Role.fromCode(name).isPresent())
                .findFirst().orElse(null);
    }

    /** Lascia all'utente un solo ruolo applicativo; i ruoli tecnici di Keycloak non si toccano. */
    private void setAppRole(String userId, String role) {
        if (Role.fromCode(role).isEmpty()) {
            throw new IllegalArgumentException("Ruolo non valido: " + role);
        }
        List<Map<String, Object>> current = call(() -> http.get().uri(admin("/users/" + userId + "/role-mappings/realm"))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve().body(LIST));
        List<Map<String, Object>> toRemove = current == null ? List.of() : current.stream()
                .filter(r -> Role.fromCode((String) r.get("name")).isPresent() && !role.equals(r.get("name")))
                .toList();
        if (!toRemove.isEmpty()) {
            call(() -> http.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(admin("/users/" + userId + "/role-mappings/realm"))
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(toRemove)
                    .retrieve().toBodilessEntity());
        }
        boolean present = current != null && current.stream().anyMatch(r -> role.equals(r.get("name")));
        if (!present) {
            // Il ruolo si legge tra quelli assegnabili all'utente: il client non ha accesso ai ruoli del realm.
            List<Map<String, Object>> available = call(() -> http.get()
                    .uri(admin("/users/" + userId + "/role-mappings/realm/available"))
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve().body(LIST));
            Map<String, Object> representation = available == null ? null : available.stream()
                    .filter(r -> role.equals(r.get("name"))).findFirst().orElse(null);
            if (representation == null) {
                throw new IdentityAdminException(UNAVAILABLE, "Ruolo " + role + " non trovato nel provider di identità");
            }
            call(() -> http.post().uri(admin("/users/" + userId + "/role-mappings/realm"))
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(List.of(representation))
                    .retrieve().toBodilessEntity());
        }
    }

    private void logout(String userId) {
        call(() -> http.post().uri(admin("/users/" + userId + "/logout"))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve().toBodilessEntity());
    }

    private static TenantUser toTenantUser(Map<String, Object> user, String role) {
        Object created = user.get("createdTimestamp");
        return new TenantUser((String) user.get("id"), (String) user.get("username"), (String) user.get("email"),
                (String) user.get("firstName"), (String) user.get("lastName"),
                Boolean.TRUE.equals(user.get("enabled")), role,
                created instanceof Number n ? Instant.ofEpochMilli(n.longValue()) : null);
    }

    private String admin(String path) {
        return base() + "/admin/realms/" + config.realm() + path;
    }

    private String base() {
        if (config.baseUrl() == null || config.baseUrl().isBlank()
                || config.clientSecret() == null || config.clientSecret().isBlank()) {
            throw new IdentityAdminException(UNAVAILABLE,
                    "Gestione utenti non configurata (PLATFORM_KEYCLOAK_URL, PLATFORM_KEYCLOAK_CLIENT_SECRET)");
        }
        return config.baseUrl().replaceAll("/+$", "");
    }

    /** Token del service account, riusato fino a 30 secondi prima della scadenza. */
    private synchronized String token() {
        Instant now = clock.instant();
        if (accessToken == null || now.isAfter(tokenExpiresAt)) {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", config.clientId());
            form.add("client_secret", config.clientSecret());
            Map<String, Object> response;
            try {
                response = http.post().uri(base() + "/realms/" + config.realm() + "/protocol/openid-connect/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                        .retrieve().body(MAP);
            } catch (RestClientException e) {
                throw new IdentityAdminException(UNAVAILABLE, "Accesso del servizio al provider di identità non riuscito", e);
            }
            if (response == null || !(response.get("access_token") instanceof String value)) {
                throw new IdentityAdminException(UNAVAILABLE, "Risposta del provider di identità non valida");
            }
            long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
            accessToken = value;
            tokenExpiresAt = now.plusSeconds(Math.max(0, expiresIn - 30));
        }
        return accessToken;
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new IdentityAdminException(NOT_FOUND, "Utente non trovato", e);
            }
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new IdentityAdminException(CONFLICT, "Nome utente o email già in uso", e);
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                accessToken = null;
            }
            throw new IdentityAdminException(UNAVAILABLE, "Il provider di identità ha rifiutato la richiesta (" + e.getStatusCode().value() + ")", e);
        } catch (IdentityAdminException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IdentityAdminException(UNAVAILABLE, "Provider di identità non raggiungibile", e);
        }
    }
}
