package it.gestionale.api.platform.identity;

/** Dati di un nuovo utente di un'azienda. */
public record NewTenantUser(String username, String email, String firstName, String lastName, String role) {
}
