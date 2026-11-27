package it.gestionale.api.accesso;

import java.util.List;

/** Voce della rubrica utenti, per scegliere i destinatari (es. delle notifiche). */
public record UtenteRubrica(String userId, String username, String name, String email, List<String> roles) {
}
