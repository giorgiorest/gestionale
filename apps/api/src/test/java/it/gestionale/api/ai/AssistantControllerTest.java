package it.gestionale.api.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.TestData;
import it.gestionale.api.TestJwt;
import it.gestionale.api.licensing.LicenseRepository;
import it.gestionale.api.tenancy.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class AssistantControllerTest extends AbstractIntegrationTest {

    static final String BODY = """
            {"messages":[{"role":"USER","content":"Quanti clienti ho?"}]}""";

    @Autowired
    MockMvc mvc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    TenantRepository tenants;
    @Autowired
    LicenseRepository licenses;

    private ResultActions chatAs(UUID tenant) throws Exception {
        return chatAs(tenant, "operator");
    }

    private ResultActions chatAs(UUID tenant, String role) throws Exception {
        return mvc.perform(post("/api/ai/chat")
                .with(TestJwt.user(tenant, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY));
    }

    @Test
    void forbiddenWhenPlanDoesNotIncludeAssistant() throws Exception {
        UUID tenant = TestData.createTenant(tx, tenants, licenses, "base", Set.of(), 100);
        chatAs(tenant).andExpect(status().isForbidden());
    }

    @Test
    void forbiddenForRoleWithoutAssistantPermission() throws Exception {
        UUID tenant = TestData.createTenant(tx, tenants, licenses, "pro", Set.of("aiAssistant"), 100);
        chatAs(tenant, "viewer").andExpect(status().isForbidden());
    }

    @Test
    void tooManyRequestsWhenQuotaIsZero() throws Exception {
        UUID tenant = TestData.createTenant(tx, tenants, licenses, "pro", Set.of("aiAssistant"), 0);
        chatAs(tenant).andExpect(status().isTooManyRequests());
    }

    @Test
    void serviceUnavailableWithNoopProvider() throws Exception {
        UUID tenant = TestData.createTenant(tx, tenants, licenses, "pro", Set.of("aiAssistant"), 100);
        chatAs(tenant).andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsEmptyConversation() throws Exception {
        UUID tenant = TestData.createTenant(tx, tenants, licenses, "pro", Set.of("aiAssistant"), 100);
        mvc.perform(post("/api/ai/chat")
                        .with(TestJwt.user(tenant, "operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
