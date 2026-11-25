-- =====================================================================
-- V11 — DailyReport: il contenitore giornaliero del rapportino (testata).
--
-- Solo la testata: niente righe di ore (WorkEntry, incremento successivo) e niente transizioni
-- vere e proprie di stato (submit/close, incremento successivo). Qui si crea/apre/salva solo un
-- DRAFT: submitted_at e closed_at sono già colonne (così la storia che implementa le transizioni
-- non deve aggiungerle con un'altra migrazione), ma nessun codice le imposta ancora.
--
-- commessa_id  Chiave esterna vera, senza ON DELETE (come già per resource_assignments e
--              work_activities): la cancellazione logica della commessa non deve cancellare a
--              cascata i rapportini.
-- (commessa_id, date)  Un solo rapportino operativo per commessa/data nell'MVP (ADR-Rapportino,
--              tabella 5 del piano): indice unico parziale, non globalmente univoco sul tenant,
--              come il code di work_activities.
-- =====================================================================

CREATE TABLE daily_reports (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    commessa_id   UUID NOT NULL REFERENCES commesse (id),
    date          DATE NOT NULL,
    weather       TEXT,
    notes         TEXT,
    status        TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'submitted', 'closed')),
    submitted_at  TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CALL add_sync_columns('daily_reports');
CALL enable_tenant_rls('daily_reports');

-- L'elenco di una commessa, più recenti prima; serve anche il vincolo di unicità applicativo.
CREATE UNIQUE INDEX daily_reports_commessa_date_idx ON daily_reports (commessa_id, date)
    WHERE deleted_at IS NULL;
