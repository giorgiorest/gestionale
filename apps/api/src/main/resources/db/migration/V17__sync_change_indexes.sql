-- =====================================================================
-- V17 — Indici per la sincronizzazione incrementale (GET /api/sync/changes, voce 1.2).
--
-- Il cursore della sincronizzazione è (updated_at, id): a differenza degli indici delle liste
-- (V2, V6-V12, V16), qui l'indice NON è parziale, perché la query deve restituire anche i
-- tombstone (deleted_at valorizzato) per propagarli ai dispositivi rimasti offline.
-- =====================================================================

CREATE INDEX customers_tenant_updated_idx ON customers (tenant_id, updated_at, id);
CREATE INDEX resources_tenant_updated_idx ON resources (tenant_id, updated_at, id);
CREATE INDEX commesse_tenant_updated_idx ON commesse (tenant_id, updated_at, id);
CREATE INDEX resource_assignments_tenant_updated_idx ON resource_assignments (tenant_id, updated_at, id);
CREATE INDEX work_activities_tenant_updated_idx ON work_activities (tenant_id, updated_at, id);
CREATE INDEX daily_reports_tenant_updated_idx ON daily_reports (tenant_id, updated_at, id);
CREATE INDEX work_entries_tenant_updated_idx ON work_entries (tenant_id, updated_at, id);
CREATE INDEX daily_report_materials_tenant_updated_idx ON daily_report_materials (tenant_id, updated_at, id);
