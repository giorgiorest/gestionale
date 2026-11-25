-- =====================================================================
-- V9 — ResourceAssignment: una Resource assegnata a una Commessa.
--
-- Il ruolo qui è *contestuale alla commessa* (es. "capocantiere" su questo cantiere, "operaio"
-- su un altro), distinto dal ruolo applicativo della persona (Role/Permission, auth/): niente
-- CHECK sull'insieme di Role, è un testo libero deciso da chi assegna.
--
-- resource_id / commessa_id  Chiavi esterne vere (resources e commesse sono anagrafiche
--                            complete, non si popolano in ritardo come app_users), senza
--                            ON DELETE: la cancellazione logica di una risorsa o di una
--                            commessa non deve cancellare a cascata le assegnazioni, le
--                            mantiene coerenti il servizio (come già per commesse.customer_id).
-- valid_from / valid_to      Periodo di validità; valid_to nullo = ancora in corso.
-- =====================================================================

CREATE TABLE resource_assignments (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    resource_id           UUID NOT NULL REFERENCES resources (id),
    commessa_id           UUID NOT NULL REFERENCES commesse (id),
    valid_from            DATE NOT NULL,
    valid_to              DATE,
    role                  TEXT NOT NULL,
    hourly_cost_override  NUMERIC(10, 2),
    active                BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CALL add_sync_columns('resource_assignments');
CALL enable_tenant_rls('resource_assignments');

-- La tab "Risorse" di una commessa elenca le sue assegnazioni, non cancellate.
CREATE INDEX resource_assignments_commessa_idx ON resource_assignments (commessa_id) WHERE deleted_at IS NULL;

-- Le assegnazioni di una risorsa (per vedere in quali commesse si trova, o validarne il tenant).
CREATE INDEX resource_assignments_resource_idx ON resource_assignments (resource_id) WHERE deleted_at IS NULL;
