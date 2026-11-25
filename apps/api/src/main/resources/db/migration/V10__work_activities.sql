-- =====================================================================
-- V10 — WorkActivity: lavorazioni di una commessa (es. "Armatura pilastri piano terra").
--
-- Il rapportino (incrementi successivi) le riuserà per classificare le ore: codice e nome
-- bastano per questa storia, lo stato e l'avanzamento sono già qui perché il capocantiere li
-- aggiorna indipendentemente da quando arriva il rapportino.
--
-- commessa_id  Chiave esterna vera (commesse è un'anagrafica completa), senza ON DELETE: la
--              cancellazione logica della commessa non deve cancellare a cascata le lavorazioni.
-- parent_id    Gerarchia (WBS) opzionale, senza costruire ancora l'albero in UI: riferimento
--              alla stessa tabella, verificato per tenant dal servizio, come resources.team_lead_id.
-- code         Univoco per (tenant, commessa), non globalmente: due commesse possono avere
--              entrambe una lavorazione "DEM" (demolizioni).
-- =====================================================================

CREATE TABLE work_activities (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    commessa_id          UUID NOT NULL REFERENCES commesse (id),
    parent_id            UUID REFERENCES work_activities (id),
    code                 TEXT NOT NULL,
    name                 TEXT NOT NULL,
    description          TEXT,
    status               TEXT NOT NULL DEFAULT 'pianificata'
                         CHECK (status IN ('pianificata', 'in_corso', 'completata')),
    progress_percentage  INTEGER NOT NULL DEFAULT 0 CHECK (progress_percentage BETWEEN 0 AND 100),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CALL add_sync_columns('work_activities');
CALL enable_tenant_rls('work_activities');

-- L'elenco di una commessa, ordinato per codice: indice parziale, come per le altre entità,
-- per saltare i tombstone; serve anche il vincolo di unicità applicativo sul codice.
CREATE UNIQUE INDEX work_activities_commessa_code_idx ON work_activities (commessa_id, code)
    WHERE deleted_at IS NULL;
