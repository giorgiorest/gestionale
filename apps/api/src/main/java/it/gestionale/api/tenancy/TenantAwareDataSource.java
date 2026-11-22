package it.gestionale.api.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Imposta {@code app.tenant_id} su ogni connessione presa dal pool, in base a {@link TenantContext}.
 *
 * <p>Il valore viene sempre riscritto (stringa vuota se non c'è tenant), quindi una connessione
 * riutilizzata dal pool non conserva il tenant della richiesta precedente. Senza tenant la
 * funzione SQL {@code current_tenant_id()} restituisce NULL e la RLS non mostra nessuna riga.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String SET_TENANT = "SELECT set_config('app.tenant_id', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyTenant(super.getConnection(username, password));
    }

    private static Connection applyTenant(Connection connection) throws SQLException {
        String tenant = TenantContext.current().map(Object::toString).orElse("");
        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
            statement.setString(1, tenant);
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
