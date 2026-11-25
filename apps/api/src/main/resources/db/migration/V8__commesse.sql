-- =====================================================================
-- V8 — Commesse (il cantiere): entità centrale del prodotto (ADR 0012).
--
-- customer_id  Cliente della commessa: chiave esterna vera (customers è
--              un'anagrafica completa quanto resources, non si popola in
--              ritardo come app_users), ma la cancellazione logica del
--              cliente non deve bloccare la cancellazione della commessa:
--              nessun ON DELETE, la coerenza la mantiene il servizio.
-- stato        Salvato in minuscolo (come TenantStatus, tenancy/), un solo
--              CHECK invece di una tabella di stati: l'insieme è piccolo e
--              stabile; se dovesse servire una macchina a stati con
--              transizioni vincolate lo si rivede allora.
-- =====================================================================

CREATE TABLE commesse (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    customer_id  UUID NOT NULL REFERENCES customers (id),
    nome         TEXT NOT NULL,
    indirizzo    TEXT,
    stato        TEXT NOT NULL DEFAULT 'pianificata'
                 CHECK (stato IN ('pianificata', 'in_corso', 'sospesa', 'conclusa')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CALL add_sync_columns('commesse');
CALL enable_tenant_rls('commesse');

-- L'elenco legge "del tenant, non cancellate, ordinate per nome": indice parziale, come
-- per clienti (V6) e risorse (V7), per saltare i tombstone e servire anche l'ordinamento.
CREATE INDEX commesse_tenant_name_idx ON commesse (tenant_id, nome) WHERE deleted_at IS NULL;

-- Le commesse di un cliente: servirà a mostrarle nella sua scheda.
CREATE INDEX commesse_customer_idx ON commesse (customer_id) WHERE deleted_at IS NULL;
