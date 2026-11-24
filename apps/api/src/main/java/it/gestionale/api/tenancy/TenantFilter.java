package it.gestionale.api.tenancy;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Legge il tenant dal claim del JWT autenticato e lo mette in {@link TenantContext}.
 *
 * <p>Il valore viene poi applicato alla connessione PostgreSQL ({@code app.tenant_id}) da
 * {@link TenantAwareDataSource} ogni volta che una connessione viene presa dal pool, così le
 * policy di Row Level Security filtrano tutte le query della richiesta.
 *
 * <p>Una richiesta autenticata senza tenant valido viene rifiutata con 403, come quella di
 * un'azienda sospesa dalla console di piattaforma ({@link TenantAccessService}).
 */
public class TenantFilter extends OncePerRequestFilter {

    private final TenancyProperties properties;
    private final TenantAccessService access;

    public TenantFilter(TenancyProperties properties, TenantAccessService access) {
        this.properties = properties;
        this.access = access;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            // Richiesta anonima su endpoint pubblico: nessun tenant.
            chain.doFilter(request, response);
            return;
        }

        UUID tenantId = parseTenant(jwtAuth.getToken().getClaimAsString(properties.tenantClaim()));
        if (tenantId == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token senza tenant valido");
            return;
        }

        if (access.isBlocked(tenantId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Azienda sospesa: contatta il fornitore del servizio");
            return;
        }

        TenantContext.set(tenantId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static UUID parseTenant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
