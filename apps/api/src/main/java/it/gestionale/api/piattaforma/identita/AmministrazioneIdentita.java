package it.gestionale.api.piattaforma.identita;

import java.util.List;
import java.util.UUID;

/**
 * Gestione degli utenti delle aziende presso il provider di identità, per la console di piattaforma.
 *
 * <p>È l'unico punto del codice che dipende dalle API amministrative del provider (oggi
 * {@link KeycloakAmministrazioneIdentita}): il resto dell'applicazione legge solo claim OIDC neutri.
 * Ogni operazione su un utente verifica che appartenga all'azienda indicata.
 */
public interface AmministrazioneIdentita {

    List<UtenteTenant> listUsers(UUID tenantId);

    int countUsers(UUID tenantId);

    /** Crea l'utente con il ruolo e una password provvisoria da cambiare al primo accesso. */
    UtenteTenant createUser(UUID tenantId, NuovoUtenteTenant user, String temporaryPassword);

    /** Attiva o disattiva l'utente e/o ne cambia il ruolo (null = invariato). */
    UtenteTenant updateUser(UUID tenantId, String userId, Boolean enabled, String role);

    /** Nuova password provvisoria; chiude le sessioni aperte. */
    void resetPassword(UUID tenantId, String userId, String temporaryPassword);
}
