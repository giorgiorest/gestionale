-- =====================================================================
-- V12 — WorkEntry: le righe di ore del rapportino giornaliero (GitHub #50).
--
-- daily_report_id  Chiave esterna vera verso daily_reports, con ON DELETE CASCADE: a differenza
--                  di commessa_id altrove, qui il rapportino è il contenitore della riga, non
--                  un'anagrafica condivisa — cancellare la testata (cosa che oggi non succede,
--                  solo la cancellazione logica) non deve lasciare righe orfane.
-- resource_id      Chiave esterna vera verso resources, senza ON DELETE (come resource_assignments
--                  e work_activities): la cancellazione logica di una risorsa non deve cancellare
--                  a cascata le righe già registrate.
-- work_activity_id Chiave esterna vera verso work_activities, stesso motivo.
-- hours / overtime_hours  Il servizio valida che non siano negative; overtime_hours ha un default
--                  perché nella UI è un campo facoltativo (straordinario), non sempre compilato.
-- =====================================================================

CREATE TABLE work_entries (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    daily_report_id   UUID NOT NULL REFERENCES daily_reports (id) ON DELETE CASCADE,
    resource_id       UUID NOT NULL REFERENCES resources (id),
    work_activity_id  UUID NOT NULL REFERENCES work_activities (id),
    hours             NUMERIC(5, 2) NOT NULL CHECK (hours >= 0),
    overtime_hours    NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (overtime_hours >= 0),
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CALL add_sync_columns('work_entries');
CALL enable_tenant_rls('work_entries');

-- L'elenco delle righe di un rapportino: è così che la UI le mostra e le somma.
CREATE INDEX work_entries_daily_report_idx ON work_entries (daily_report_id) WHERE deleted_at IS NULL;
