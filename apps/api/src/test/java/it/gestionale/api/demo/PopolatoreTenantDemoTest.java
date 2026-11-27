package it.gestionale.api.demo;

import static it.gestionale.api.demo.PopolatoreTenantDemo.DEMO_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import it.gestionale.api.TestIntegrazioneBase;
import it.gestionale.api.clienti.Cliente;
import it.gestionale.api.clienti.ClienteRepository;
import it.gestionale.api.commesse.CommessaRepository;
import it.gestionale.api.licenze.AbilitazioniService;
import it.gestionale.api.risorse.RisorsaRepository;
import it.gestionale.api.tenant.ContestoTenant;
import it.gestionale.api.tenant.Tenant;
import it.gestionale.api.tenant.TenantRepository;
import it.gestionale.api.tenant.TipoTenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "gestionale.demo.enabled=true")
class PopolatoreTenantDemoTest extends TestIntegrazioneBase {

    @Autowired
    PopolatoreTenantDemo seeder;
    @Autowired
    TenantRepository tenants;
    @Autowired
    ClienteRepository customers;
    @Autowired
    RisorsaRepository resources;
    @Autowired
    CommessaRepository commesse;
    @Autowired
    AbilitazioniService entitlements;

    @Test
    void seedsDemoTenantAndResetRestoresInitialData() {
        ContestoTenant.runAs(DEMO_TENANT_ID, () -> {
            assertThat(tenants.findById(DEMO_TENANT_ID)).map(Tenant::getKind).contains(TipoTenant.DEMO);
            assertThat(customers.countByTenantIdAndDeletedAtIsNull(DEMO_TENANT_ID)).isEqualTo(3);
            assertThat(resources.findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(DEMO_TENANT_ID))
                    .hasSize(2);
            assertThat(commesse.findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(DEMO_TENANT_ID)).hasSize(1);
            assertThat(entitlements.current().plan()).isEqualTo("demo");

            customers.save(new Cliente(UUID.randomUUID(), DEMO_TENANT_ID, "Modifica dell'utente demo", null, "demo"));
            assertThat(customers.countByTenantIdAndDeletedAtIsNull(DEMO_TENANT_ID)).isEqualTo(4);
        });

        seeder.reset();

        ContestoTenant.runAs(DEMO_TENANT_ID, () -> {
            assertThat(customers.countByTenantIdAndDeletedAtIsNull(DEMO_TENANT_ID)).isEqualTo(3);
            assertThat(resources.findByTenantIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(DEMO_TENANT_ID))
                    .hasSize(2);
            assertThat(commesse.findByTenantIdAndDeletedAtIsNullOrderByNomeAsc(DEMO_TENANT_ID)).hasSize(1);
            assertThat(entitlements.current().plan()).isEqualTo("demo");
        });
    }
}
