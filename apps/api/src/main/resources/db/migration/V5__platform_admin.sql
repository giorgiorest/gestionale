-- ---------------------------------------------------------------------
-- Console di amministrazione della piattaforma (ADR 0010).
--
-- Gli amministratori della piattaforma gestiscono tutte le aziende: l'API lavora per loro con
-- app.worker = 'platform-admin' (WorkerContext). La RLS resta attiva: si aggiungono policy
-- dedicate SOLO sulle tabelle che servono (aziende, licenze, registro), che si sommano a
-- tenant_isolation. Le tabelle di dominio (clienti, notifiche, …) restano visibili solo al tenant.
-- ---------------------------------------------------------------------

CREATE POLICY platform_admin ON tenants
    USING (current_setting('app.worker', true) = 'platform-admin')
    WITH CHECK (current_setting('app.worker', true) = 'platform-admin');

CREATE POLICY platform_admin ON licenses
    USING (current_setting('app.worker', true) = 'platform-admin')
    WITH CHECK (current_setting('app.worker', true) = 'platform-admin');

-- Registro delle operazioni della console: chi ha fatto cosa, su quale azienda.
-- Mai password o segreti nei dettagli.
CREATE TABLE platform_audit (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    actor_id    TEXT        NOT NULL,                  -- claim sub dell'amministratore (realm piattaforma)
    actor_name  TEXT,
    action      TEXT        NOT NULL,                  -- es. tenant.create, user.disable
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX platform_audit_tenant_idx ON platform_audit (tenant_id, created_at DESC);
CALL enable_tenant_rls('platform_audit');
CREATE POLICY platform_admin ON platform_audit
    USING (current_setting('app.worker', true) = 'platform-admin')
    WITH CHECK (current_setting('app.worker', true) = 'platform-admin');
