-- =====================================================================
-- V13 — WorkEntryCostSnapshot: storico del costo manodopera alla chiusura del rapportino
-- (GitHub #51). Una riga per ogni WorkEntry attiva, scritta da DailyReportService.close()
-- nella stessa transazione della transizione di stato: se in seguito cambia la tariffa di un
-- ResourceAssignment, lo storico non deve cambiare con essa.
--
-- Non è un'entità sincronizzata dal client: niente CALL add_sync_columns (niente deleted_at,
-- version, created_by, updated_by). È scritta solo dal server, mai da un upsert/outbox, ed è un
-- registro di sola lettura una volta creato — stesso trattamento di platform_audit (V5).
--
-- hourly_cost / labor_cost sono NULL quando la risorsa non ha una tariffa oraria nota in quella
-- data: ResourceAssignment.hourly_cost_override è facoltativo e su Resource non esiste ancora una
-- tariffa standard di fallback (decisione di prodotto non presa, non inventata qui). Un rapportino
-- si chiude comunque; quelle righe restano con il costo sconosciuto, non a zero.
-- work_entry_id  Chiave esterna vera verso work_entries, senza ON DELETE (come resource_id e
--                work_activity_id in work_entries, V12): la riga non è mai cancellata in modo
--                indipendente dal rapportino che la contiene.
-- daily_report_id  Chiave esterna vera verso daily_reports, con ON DELETE CASCADE: è il
--                contenitore di questo snapshot, stesso trattamento di work_entries.daily_report_id.
-- =====================================================================

CREATE TABLE work_entry_cost_snapshots (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    daily_report_id   UUID NOT NULL REFERENCES daily_reports (id) ON DELETE CASCADE,
    work_entry_id     UUID NOT NULL REFERENCES work_entries (id),
    resource_id       UUID NOT NULL REFERENCES resources (id),
    work_activity_id  UUID NOT NULL REFERENCES work_activities (id),
    hours             NUMERIC(5, 2) NOT NULL,
    overtime_hours    NUMERIC(5, 2) NOT NULL,
    hourly_cost       NUMERIC(10, 2),
    labor_cost        NUMERIC(10, 2),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CALL enable_tenant_rls('work_entry_cost_snapshots');

-- Lo storico di un rapportino: è così che una futura voce 3.5 (riepiloghi costi) lo leggerà.
CREATE INDEX work_entry_cost_snapshots_report_idx ON work_entry_cost_snapshots (daily_report_id);
