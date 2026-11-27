package it.gestionale.api.piattaforma.identita;

/** Dati di un nuovo utente di un'azienda. */
public record NuovoUtenteTenant(String username, String email, String firstName, String lastName, String role) {
}
