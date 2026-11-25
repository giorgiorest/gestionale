package it.gestionale.api.demo;

import static it.gestionale.api.demo.DemoTenantSeeder.DEMO_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import it.gestionale.api.AbstractIntegrationTest;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.customers.Customer;
import it.gestionale.api.customers.CustomerRepository;
import it.gestionale.api.licensing.EntitlementService;
import it.gestionale.api.resources.ResourceRepository;
import it.gestionale.api.tenancy.Tenant;
import it.gestionale.api.tenancy.TenantContext;
import it.gestionale.api.tenancy.TenantKind;
import it.gestionale.api.tenancy.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "gestionale.demo.enabled=true")
class DemoTenantSeederTest extends AbstractIntegrationTest {

    @Autowired
    DemoTenantSeeder seeder;
    @Autowired
    TenantRepository tenants;
    @Autowired
    CustomerRepository customers;
    @Autowired
    ResourceRepository resources;
    @Autowired
    CommessaRepository commesse;
    @Autowired
    EntitlementService entitlements;

    @Test
    void seedsDemoTenantAndResetRestoresInitialData() {
        TenantContext.runAs(DEMO_TENANT_ID, () -> {
            assertThat(tenants.findById(DEMO_TENANT_ID)).map(Tenant::getKind).contains(TenantKind.DEMO);
            assertThat(customers.countByTenantIdAndDeletedAtIsNull(DEMO_TENANT_ID)).isEqualTo(3);
            assertThat(resources.findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(DEMO_TENANT_ID))
                    .hasSize(2);
            assertThat(commesse.findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(DEMO_TENANT_ID)).hasSize(1);
            assertThat(entitlements.current().plan()).isEqualTo("demo");

            customers.save(new Customer(UUID.randomUUID(), DEMO_TENANT_ID, "Modifica dell'utente demo", null, "demo"));
            assertThat(customers.countByTenantIdAndDeletedAtIsNull(DEMO_TENANT_ID)).isEqualTo(4);
        });

        seeder.reset();

        TenantContext.runAs(DEMO_TENANT_ID, () -> {
            assertThat(customers.countByTenantIdAndDeletedAtIsNull(DEMO_TENANT_ID)).isEqualTo(3);
            assertThat(resources.findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(DEMO_TENANT_ID))
                    .hasSize(2);
            assertThat(commesse.findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(DEMO_TENANT_ID)).hasSize(1);
            assertThat(entitlements.current().plan()).isEqualTo("demo");
        });
    }
}
