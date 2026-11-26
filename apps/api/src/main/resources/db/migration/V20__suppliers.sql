-- =====================================================================
-- V20 — Fornitori (roadmap 2.2): anagrafica a parte, non condivisa con customers.
--
-- Stessi campi del mestiere di customers (dopo V19), perché un fornitore è lo stesso tipo di
-- anagrafica di un cliente (ragione sociale, dati fiscali, PEC/SDI, condizioni di pagamento), ma
-- tabella propria: le estensioni della Fase E (categoria fornitore, IBAN, referente commerciale)
-- riguardano solo i fornitori e non devono toccare l'anagrafica clienti né forzare quei campi su
-- un cliente che non li avrà mai.
-- =====================================================================

CREATE TABLE suppliers (
    id                    UUID        PRIMARY KEY,           -- generato dal client
    tenant_id             UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name                  TEXT        NOT NULL,
    email                 TEXT,
    partita_iva           TEXT,
    codice_fiscale        TEXT,
    indirizzo             TEXT,
    pec                   TEXT,
    codice_sdi            TEXT,
    condizioni_pagamento  TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CALL add_sync_columns('suppliers');
CALL enable_tenant_rls('suppliers');

-- Come per customers (V6, V18): elenco "del tenant, non cancellati, ordinati per nome" e
-- ricerca libera (?q=) su nome/email con pg_trgm (estensione già creata in V18).
CREATE INDEX suppliers_tenant_name_idx ON suppliers (tenant_id, name) WHERE deleted_at IS NULL;
CREATE INDEX suppliers_name_trgm_idx ON suppliers USING gin (name gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX suppliers_email_trgm_idx ON suppliers USING gin (email gin_trgm_ops) WHERE deleted_at IS NULL;
