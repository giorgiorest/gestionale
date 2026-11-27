package it.gestionale.api.tenant;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Avvolge il DataSource applicativo in {@link DataSourceTenant}.
 * Flyway usa un DataSource proprio (spring.flyway.url/user) e non passa da qui.
 */
@Configuration
public class DataSourceTenantConfig {

    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof DataSourceTenant)) {
                    return new DataSourceTenant(dataSource);
                }
                return bean;
            }
        };
    }
}
