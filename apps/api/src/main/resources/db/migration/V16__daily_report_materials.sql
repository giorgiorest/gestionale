-- =====================================================================
-- V16 — DailyReportMaterial: le righe di materiale del rapportino giornaliero (roadmap 3.2).
--
-- Nessun catalogo materiali esiste ancora (GitHub #55, docs/roadmap-alex.md C.1): description e
-- unit sono testo libero, non un riferimento a un'anagrafica — da rivedere quando quella storia
-- arriva (trigger di revisione), senza duplicarla ora.
--
-- daily_report_id  Chiave esterna vera verso daily_reports, con ON DELETE CASCADE: come per
--                  work_entries, il rapportino è il contenitore della riga, non un'anagrafica
--                  condivisa.
-- quantity         Il servizio valida che sia positiva (non zero): una riga di materiale senza
--                  quantità non ha senso.
-- =====================================================================

CREATE TABLE daily_report_materials (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    daily_report_id  UUID NOT NULL REFERENCES daily_reports (id) ON DELETE CASCADE,
    description      TEXT NOT NULL,
    quantity         NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit             TEXT NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CALL add_sync_columns('daily_report_materials');
CALL enable_tenant_rls('daily_report_materials');

-- L'elenco delle righe di un rapportino: è così che la UI le mostra.
CREATE INDEX daily_report_materials_daily_report_idx ON daily_report_materials (daily_report_id)
    WHERE deleted_at IS NULL;
