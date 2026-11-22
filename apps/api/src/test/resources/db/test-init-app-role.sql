-- Ruolo applicativo per i test: non superuser e non proprietario delle tabelle,
-- così la Row Level Security si applica come in produzione.
CREATE ROLE gestionale_app LOGIN PASSWORD 'gestionale_app' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
