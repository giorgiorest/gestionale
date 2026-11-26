-- =====================================================================
-- V19 — Anagrafica clienti completa (roadmap 2.1): dati fiscali e commerciali.
--
-- Tutte le colonne sono nullable: i clienti già in tabella (anche quelli del
-- tenant demo) non hanno questi dati, e un cliente privato può non avere
-- partita IVA. La validazione del formato (P.IVA, codice fiscale, codice
-- destinatario SDI) sta nel DTO Input lato Java, non qui: è dati applicativi,
-- non un vincolo che la tabella deve far rispettare da sola.
-- =====================================================================

ALTER TABLE customers
    ADD COLUMN partita_iva           TEXT,
    ADD COLUMN codice_fiscale        TEXT,
    ADD COLUMN indirizzo             TEXT,
    ADD COLUMN pec                   TEXT,
    ADD COLUMN codice_sdi            TEXT,
    ADD COLUMN condizioni_pagamento  TEXT;
