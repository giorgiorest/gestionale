-- =====================================================================
-- V18 — Indice trigram per la ricerca libera (?q=) della lista paginata di customers (ADR 0014).
--
-- L'indice per l'ordinamento predefinito (tenant_id, name) esiste già da V6 (introdotto in
-- anticipo, insieme al predicato "WHERE deleted_at IS NULL" che la lista usa). pg_trgm supporta
-- direttamente gli operatori ILIKE usati da "q", senza bisogno di normalizzare il caso con lower().
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX customers_name_trgm_idx ON customers USING gin (name gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX customers_email_trgm_idx ON customers USING gin (email gin_trgm_ops) WHERE deleted_at IS NULL;
