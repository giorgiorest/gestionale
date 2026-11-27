package it.gestionale.api.accesso;

import java.util.List;

/**
 * Pubblicato (in modo sincrono, nella transazione corrente) quando un utente compare per la prima
 * volta nella rubrica del tenant. Gli altri moduli vi si agganciano con {@code @EventListener}.
 */
public record UtenteRegistrato(String userId, List<String> roles) {
}
