-- Registro delle chiamate all'assistente AI: tenant, utente, modello, token e costo stimato.
-- Serve per la quota mensile (ai_quota_monthly) e per la rendicontazione.
CREATE TABLE ai_usage (
    id                  UUID           PRIMARY KEY,
    tenant_id           UUID           NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id             TEXT           NOT NULL,
    model               TEXT           NOT NULL,
    input_tokens        INTEGER        NOT NULL DEFAULT 0,
    output_tokens       INTEGER        NOT NULL DEFAULT 0,
    estimated_cost_eur  NUMERIC(12, 6) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX ai_usage_tenant_id_idx ON ai_usage (tenant_id, created_at);
CALL enable_tenant_rls('ai_usage');
