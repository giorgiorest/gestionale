-- =====================================================================
-- V7 — Risorse e squadre (roadmap-alex.md A.2 — dipendenti che compilano i rapportini)
--
-- resources  Persona del tenant (dipendente). Collegata, quando esiste, alla rubrica
--            app_users (V4): user_id è lo stesso claim `sub` di app_users.user_id, ma
--            senza FK, perché app_users si popola solo al primo accesso (una risorsa
--            può essere creata prima che la persona abbia mai effettuato il login).
--            team_lead_id: la risorsa di cui questa persona fa parte della squadra
--            ("capo squadra"); un riferimento alla stessa tabella, verificato per
--            tenant dal servizio (il vincolo di chiave esterna non conosce la RLS).
--            Le squadre sono a un solo livello: un capo squadra non può a sua volta
--            avere un capo squadra (anche questo verificato dal servizio, non dal
--            database), così un ciclo non è mai rappresentabile.
-- =====================================================================

CREATE TABLE resources (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    first_name    TEXT NOT NULL,
    last_name     TEXT NOT NULL,
    user_id       TEXT,
    team_lead_id  UUID REFERENCES resources (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CALL add_sync_columns('resources');
CALL enable_tenant_rls('resources');

-- L'elenco legge "del tenant, non cancellate, ordinate per cognome e nome": indice parziale,
-- come per i clienti (V6), per saltare i tombstone e servire anche l'ordinamento.
CREATE INDEX resources_tenant_name_idx ON resources (tenant_id, last_name, first_name) WHERE deleted_at IS NULL;

-- Una persona ha al più una risorsa attiva collegata al suo accesso.
CREATE UNIQUE INDEX resources_tenant_user_idx ON resources (tenant_id, user_id)
    WHERE deleted_at IS NULL AND user_id IS NOT NULL;
