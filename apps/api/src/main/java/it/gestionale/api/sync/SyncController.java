package it.gestionale.api.sync;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sincronizzazione incrementale verso i dispositivi offline (voce 1.2 della roadmap). Nessun
 * {@code @PreAuthorize} qui: l'endpoint copre più entità, ognuna con il proprio permesso, filtrate
 * in {@link SyncService}. Resta comunque richiesta l'autenticazione, come per ogni altro
 * endpoint (vedi {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    @GetMapping("/changes")
    public SyncChangesDto changes(@RequestParam(required = false) String cursor,
                                  @RequestParam(required = false) Integer limit) {
        return service.changes(cursor, limit, grantedAuthorityCodes());
    }

    private static Set<String> grantedAuthorityCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? Set.of()
                : authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
    }
}
