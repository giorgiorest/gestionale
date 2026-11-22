-- =====================================================================
-- V1 — Baseline multi-tenant
--
-- Regola del progetto: ogni tabella di dominio ha
--   tenant_id UUID NOT NULL REFERENCES tenants(id) + indice su tenant_id
-- e la Row Level Security attiva (vedi enable_tenant_rls più sotto).
--
-- Le migrazioni girano con l'utente proprietario dello schema; l'applicazione
-- si collega con il ruolo ${appRole}, che non è superuser né proprietario.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tenant: un'azienda cliente oppure un tenant demo.
-- ---------------------------------------------------------------------
CREATE TABLE tenants (
    id          UUID        PRIMARY KEY,
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL UNIQUE,
    kind        TEXT        NOT NULL CHECK (kind IN ('customer', 'demo')),
    status      TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'expired')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Licenze: definiscono gli entitlement commerciali del tenant.
-- signature è valorizzata solo per le licenze offline (token firmato).
-- ---------------------------------------------------------------------
CREATE TABLE licenses (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    plan              TEXT        NOT NULL,
    max_users         INTEGER     NOT NULL CHECK (max_users > 0),
    features          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    ai_quota_monthly  INTEGER     NOT NULL DEFAULT 0 CHECK (ai_quota_monthly >= 0),
    valid_from        TIMESTAMPTZ NOT NULL,
    valid_until       TIMESTAMPTZ,
    signature         TEXT,
    CONSTRAINT licenses_validity_chk CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE INDEX licenses_tenant_id_idx ON licenses (tenant_id);

-- ---------------------------------------------------------------------
-- Tenant corrente della sessione.
-- L'applicazione imposta app.tenant_id su ogni connessione presa dal pool
-- (TenantAwareDataSource). Se il parametro manca o è vuoto la funzione
-- restituisce NULL e le policy non lasciano passare alcuna riga (fail-closed).
-- ---------------------------------------------------------------------
CREATE FUNCTION current_tenant_id() RETURNS UUID
    LANGUAGE sql
    STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;

-- ---------------------------------------------------------------------
-- Attivazione della Row Level Security sulle tabelle di dominio.
--
-- COME APPLICARLA A OGNI NUOVA TABELLA (in una nuova migrazione):
--
--   CREATE TABLE orders (
--       id         UUID PRIMARY KEY,
--       tenant_id  UUID NOT NULL REFERENCES tenants (id),
--       ...
--   );
--   CREATE INDEX orders_tenant_id_idx ON orders (tenant_id);
--   CALL enable_tenant_rls('orders');
--
-- La procedura abilita e FORZA la RLS (vale anche per il proprietario
-- della tabella, purché non superuser) e crea la policy tenant_isolation,
-- che filtra le letture e blocca scritture su tenant diversi dal corrente.
-- ---------------------------------------------------------------------
CREATE PROCEDURE enable_tenant_rls(target regclass)
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', target);
    EXECUTE format(
        'CREATE POLICY tenant_isolation ON %s '
        'USING (tenant_id = current_tenant_id()) '
        'WITH CHECK (tenant_id = current_tenant_id())',
        target);
END;
$$;

CALL enable_tenant_rls('licenses');

-- La tabella tenants non ha tenant_id: ogni tenant vede solo la propria riga.
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenants
    USING (id = current_tenant_id())
    WITH CHECK (id = current_tenant_id());

-- ---------------------------------------------------------------------
-- Privilegi del ruolo applicativo: solo DML, niente DDL.
-- I privilegi di default coprono anche le tabelle create dalle migrazioni future.
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${appRole}') THEN
        EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', '${appRole}');
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', '${appRole}');
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', '${appRole}');
        -- Lo storico di Flyway non riguarda l'applicazione.
        EXECUTE format('REVOKE ALL ON TABLE "${flyway:table}" FROM %I', '${appRole}');
    ELSE
        RAISE WARNING 'Ruolo applicativo "%" inesistente: privilegi non concessi', '${appRole}';
    END IF;
END;
$$;
