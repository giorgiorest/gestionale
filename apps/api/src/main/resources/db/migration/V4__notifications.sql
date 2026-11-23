-- =====================================================================
-- V4 — Notifiche e comunicazioni ai dipendenti
--
-- app_users               rubrica locale degli utenti del tenant (dai claim del token, a ogni accesso)
-- notifications           messaggio (di sistema o comunicazione di un amministratore)
-- notification_recipients destinatari con stato: letta, presa visione
-- email_deliveries        coda di invio email
-- =====================================================================

-- Rubrica: serve a risolvere i destinatari "ruolo" e "tutta l'azienda" senza dipendere
-- dalle API del provider di identità. Contiene solo chi ha già effettuato almeno un accesso.
CREATE TABLE app_users (
    tenant_id      UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id        TEXT        NOT NULL,                 -- claim sub
    username       TEXT,
    name           TEXT,
    email          TEXT,
    roles          TEXT[]      NOT NULL DEFAULT '{}',    -- ruoli applicativi (admin, operator, viewer)
    first_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id)
);
CREATE INDEX app_users_tenant_id_idx ON app_users (tenant_id);
CALL enable_tenant_rls('app_users');

CREATE TABLE notifications (
    id              UUID        PRIMARY KEY,             -- generato dal client per le comunicazioni
    tenant_id       UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    kind            TEXT        NOT NULL CHECK (kind IN ('system', 'announcement')),
    type            TEXT        NOT NULL,                -- es. "announcement", "customers.assigned"
    title           TEXT        NOT NULL,
    body            TEXT        NOT NULL DEFAULT '',
    link            TEXT,                                -- percorso nell'app, es. /clienti
    audience_type   TEXT        NOT NULL CHECK (audience_type IN ('user', 'role', 'tenant')),
    audience_value  TEXT,                                -- user_id o ruolo; NULL per tutta l'azienda
    requires_ack    BOOLEAN     NOT NULL DEFAULT false,  -- richiede la presa visione
    send_email      BOOLEAN     NOT NULL DEFAULT false,
    sender_user_id  TEXT,                                -- NULL per le notifiche di sistema
    sender_name     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notifications_audience_chk CHECK ((audience_type = 'tenant') = (audience_value IS NULL))
);
CREATE INDEX notifications_tenant_id_idx ON notifications (tenant_id, created_at DESC);
CALL enable_tenant_rls('notifications');

CREATE TABLE notification_recipients (
    notification_id  UUID        NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id          TEXT        NOT NULL,
    assigned_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at          TIMESTAMPTZ,
    acknowledged_at  TIMESTAMPTZ,                        -- ora del server alla ricezione della conferma
    PRIMARY KEY (notification_id, user_id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES app_users (tenant_id, user_id) ON DELETE CASCADE
);
CREATE INDEX notification_recipients_tenant_id_idx ON notification_recipients (tenant_id);
CREATE INDEX notification_recipients_user_idx ON notification_recipients (tenant_id, user_id, read_at);
CALL enable_tenant_rls('notification_recipients');

-- Coda email. Oltre alla policy per tenant, il processo di invio (che serve tutti i tenant)
-- vede le righe solo se dichiara esplicitamente il proprio ruolo: app.worker = 'email-dispatcher'.
CREATE TABLE email_deliveries (
    id               UUID        PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    notification_id  UUID        NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id          TEXT        NOT NULL,
    to_address       TEXT        NOT NULL,
    subject          TEXT        NOT NULL,
    body_text        TEXT        NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sent', 'failed')),
    attempts         INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ
);
CREATE INDEX email_deliveries_tenant_id_idx ON email_deliveries (tenant_id);
CREATE INDEX email_deliveries_pending_idx ON email_deliveries (next_attempt_at) WHERE status = 'pending';
CALL enable_tenant_rls('email_deliveries');
CREATE POLICY email_dispatcher ON email_deliveries
    USING (current_setting('app.worker', true) = 'email-dispatcher')
    WITH CHECK (current_setting('app.worker', true) = 'email-dispatcher');
