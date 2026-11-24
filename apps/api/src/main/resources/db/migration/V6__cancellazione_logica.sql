-- =====================================================================
-- V6 — Fondamenta della sincronizzazione: cancellazione logica,
--      blocco ottimistico e tracciatura di chi scrive.
--
-- deleted_at  Cancellazione logica. La riga resta in tabella come
--             "tombstone": è l'unico modo di propagare una cancellazione
--             ai dispositivi che erano offline (GET /api/sync/changes,
--             voce 1.2 della roadmap). NULL = record attivo.
-- version     Blocco ottimistico (@Version di JPA): due scritture
--             concorrenti sullo stesso record non si sovrascrivono in
--             silenzio.
-- created_by  Claim `sub` di chi ha creato il record.
-- updated_by  Claim `sub` di chi lo ha modificato o cancellato.
--             TEXT come app_users.user_id: l'id del provider di identità
--             non è necessariamente un UUID. NULL per i record scritti da
--             un processo di sistema (es. dati di esempio del tenant demo).
--
-- Le colonne sono nullable (o con un default) perché la tabella contiene
-- già dei record: la migrazione non richiede di inventare valori.
-- =====================================================================

-- Come enable_tenant_rls (V1): la plumbing condivisa sta in una procedura, non in quattro righe
-- da ricopiare in ogni CREATE TABLE. Una tabella nuova fa:
--   CALL add_sync_columns('commesse');
--   CALL enable_tenant_rls('commesse');
CREATE PROCEDURE add_sync_columns(target regclass)
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format(
        'ALTER TABLE %s '
        'ADD COLUMN deleted_at TIMESTAMPTZ, '
        'ADD COLUMN version    BIGINT NOT NULL DEFAULT 0, '
        'ADD COLUMN created_by TEXT, '
        'ADD COLUMN updated_by TEXT',
        target);
    EXECUTE format(
        'COMMENT ON COLUMN %s.deleted_at IS %L',
        target, 'Cancellazione logica: NULL = attivo, valorizzato = tombstone');
END;
$$;

CALL add_sync_columns('customers');

-- L'elenco legge "del tenant, non cancellati, ordinati per nome": un indice parziale salta i
-- tombstone (che si accumulano) e serve anche l'ordinamento. Regola di ADR 0014 ristretta al
-- predicato introdotto qui.
CREATE INDEX customers_tenant_name_idx ON customers (tenant_id, name) WHERE deleted_at IS NULL;
