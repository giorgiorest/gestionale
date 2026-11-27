package it.gestionale.api.osservabilita;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import it.gestionale.api.tenant.ContestoTenant;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Nessun contesto Spring: il filtro non ha dipendenze, verificarlo con MockMvc costerebbe di più. */
class ContestoLogRichiestaFilterTest {

    private final ContestoLogRichiestaFilter filter = new ContestoLogRichiestaFilter();

    @Test
    void generatesAndEchoesRequestIdWhenNotProvided() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] mdcDuringRequest = new String[2];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcDuringRequest[0] = MDC.get("request_id");
                mdcDuringRequest[1] = MDC.get("tenant_id");
            }
        };

        filter.doFilter(request, response, chain);

        String requestId = response.getHeader(ContestoLogRichiestaFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(mdcDuringRequest[0]).isEqualTo(requestId);
        assertThat(mdcDuringRequest[1]).isNull();
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void propagatesIncomingRequestIdAndTenantFromContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ContestoLogRichiestaFilter.REQUEST_ID_HEADER, "richiesta-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID tenantId = UUID.randomUUID();
        String[] mdcTenant = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcTenant[0] = MDC.get("tenant_id");
            }
        };

        ContestoTenant.runAs(tenantId, () -> {
            try {
                filter.doFilter(request, response, chain);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(response.getHeader(ContestoLogRichiestaFilter.REQUEST_ID_HEADER)).isEqualTo("richiesta-123");
        assertThat(mdcTenant[0]).isEqualTo(tenantId.toString());
        assertThat(MDC.get("tenant_id")).isNull();
    }
}
