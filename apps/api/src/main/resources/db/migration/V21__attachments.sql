-- =====================================================================
-- V21 — Allegati lato backend (roadmap 2.4, ADR 0016): metadati soltanto.
--
-- I byte non passano mai da questa tabella né dall'API: stanno nell'archivio compatibile S3,
-- alla chiave storage_key. Qui c'è solo ciò che serve a controllare permesso/tenant ed emettere
-- un URL firmato (ADR 0016 punto 6).
--
-- entity_type/entity_id  Entità e record a cui l'allegato è appeso (es. "daily_reports",
--                        l'id del rapportino). Nessuna foreign key: un'unica tabella di allegati
--                        serve entità diverse, e un record collegato può essere cancellato
--                        logicamente senza dover prima ripulire i suoi allegati.
-- storage_key            Struttura "<tenant_id>/<entity_type>/<entity_id>/<id>" (ADR 0016 punto 5),
--                        fissata alla richiesta dell'URL di caricamento (prima riga, sha256 NULL) e
--                        mai ricalcolata dopo: così la conferma non può finire su una chiave diversa
--                        da quella firmata per il caricamento.
-- sha256                 NULL finché l'allegato è "in caricamento" (URL firmato emesso, bytes non
--                        ancora confermati); valorizzato dalla conferma, dopo il caricamento. Un
--                        allegato con sha256 NULL non compare in nessun elenco.
-- =====================================================================

CREATE TABLE attachments (
    id                  UUID        PRIMARY KEY,           -- generato dal client
    tenant_id           UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    entity_type         TEXT        NOT NULL,
    entity_id           UUID        NOT NULL,
    original_filename   TEXT        NOT NULL,
    mime_type           TEXT        NOT NULL,
    size_bytes          BIGINT      NOT NULL,
    sha256              TEXT,
    storage_key         TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CALL add_sync_columns('attachments');
CALL enable_tenant_rls('attachments');

-- Elenco degli allegati di un record ("le foto di questo rapportino"), più recenti prima: la query
-- sempre fatta, con l'ordinamento già incluso nell'indice (niente filesort).
CREATE INDEX attachments_entity_idx ON attachments (tenant_id, entity_type, entity_id, created_at DESC)
    WHERE deleted_at IS NULL;
