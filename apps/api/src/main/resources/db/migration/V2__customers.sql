-- Prima tabella di dominio: anagrafica clienti del tenant.
-- Usata anche dal tenant demo come dato di esempio.
CREATE TABLE customers (
    id          UUID        PRIMARY KEY,           -- generato dal client
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    email       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX customers_tenant_id_idx ON customers (tenant_id);
CALL enable_tenant_rls('customers');
