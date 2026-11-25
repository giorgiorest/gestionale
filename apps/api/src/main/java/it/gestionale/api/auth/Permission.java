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
    USERS_MANAGE("users:manage"),
    RESOURCES_READ("resources:read"),
    RESOURCES_WRITE("resources:write"),
    COMMESSE_READ("commesse:read"),
    COMMESSE_WRITE("commesse:write"),
    RESOURCE_ASSIGNMENTS_READ("resource-assignments:read"),
    RESOURCE_ASSIGNMENTS_WRITE("resource-assignments:write"),
    WORK_ACTIVITIES_READ("work-activities:read"),
    WORK_ACTIVITIES_WRITE("work-activities:write"),
    DAILY_REPORTS_READ("daily-reports:read"),
    DAILY_REPORTS_WRITE("daily-reports:write");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
