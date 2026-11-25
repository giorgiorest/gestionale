-- ---------------------------------------------------------------------
-- Storia #54: notifica "rapportino mancante", controllata da un job che gira per tutti i
-- tenant (app.worker = 'daily-report-watchdog', WorkerContext), come il dispatcher email.
--
-- Serve solo a elencare le aziende attive: policy dedicata SOLO su tenants (in lettura). Le
-- tabelle di dominio (commesse, daily_reports, notifications) restano filtrate per tenant: il
-- job le interroga un'azienda alla volta nel suo contesto (TenantContext), senza bypassare la RLS.
-- ---------------------------------------------------------------------

CREATE POLICY daily_report_watchdog ON tenants
    FOR SELECT
    USING (current_setting('app.worker', true) = 'daily-report-watchdog');
