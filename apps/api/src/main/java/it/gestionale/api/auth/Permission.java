package it.gestionale.api.auth;

/**
 * Permessi applicativi. Sono le autorità controllate dagli endpoint ({@code @PreAuthorize}).
 * Il codice (es. {@code customers:write}) è condiviso con il frontend.
 */
public enum Permission {
    CUSTOMERS_READ("customers:read"),
    CUSTOMERS_WRITE("customers:write"),
    ASSISTANT_USE("assistant:use"),
    NOTIFICATIONS_SEND("notifications:send"),
    USERS_MANAGE("users:manage");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
