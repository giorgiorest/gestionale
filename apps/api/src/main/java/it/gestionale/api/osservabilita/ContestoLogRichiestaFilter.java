package it.gestionale.api.osservabilita;

import java.io.IOException;
import java.util.UUID;

import it.gestionale.api.tenant.ContestoTenant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mette {@code request_id} e {@code tenant_id} in {@link MDC}, così i log strutturati di tutta la
 * richiesta (compresi quelli emessi dal codice di dominio, senza bisogno di passarli esplicitamente)
 * si possono correlare tra loro e con il tenant.
 *
 * <p>Registrato in {@code SicurezzaConfig} subito dopo {@code TenantFilter}: il tenant è già nel
 * {@link ContestoTenant} a quel punto (o assente, per le richieste anonime).
 */
public class ContestoLogRichiestaFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String REQUEST_ID_MDC_KEY = "request_id";
    static final String TENANT_ID_MDC_KEY = "tenant_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = incomingOrNewRequestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        ContestoTenant.current().ifPresent(tenantId -> MDC.put(TENANT_ID_MDC_KEY, tenantId.toString()));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
            MDC.remove(TENANT_ID_MDC_KEY);
        }
    }

    private static String incomingOrNewRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
    }
}
