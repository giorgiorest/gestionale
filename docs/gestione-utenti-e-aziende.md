# Gestire utenti, ruoli e aziende

Come si amministra un'installazione di Civil Beefy: aziende clienti (tenant), licenze e utenti.

> **Il modo normale è la [console di piattaforma](console-piattaforma.md)** (`https://<dominio>/console`):
> crei l'azienda, la sua licenza e i suoi utenti senza SQL né console di Keycloak. Questa guida spiega
> come è organizzato il sistema e le procedure manuali, utili in emergenza o per operazioni che la
> console non fa (es. chiusura definitiva di un'azienda).

## Come è organizzato

```
Keycloak (realm "gestionale")                 Database (PostgreSQL)
  utente ──── attributo tenant_id ─────────▶  tenants (azienda)  ──▶ licenses (piano, funzioni)
        └──── ruoli: admin / operator / viewer              └──▶ dati dell'azienda (clienti, notifiche…)
               e di cantiere: capocantiere / caposquadra / operaio / amministrativo
```

- **Una sola installazione** serve più aziende: ogni utente appartiene a un'azienda tramite
  l'attributo `tenant_id`, e vede **solo** i dati di quell'azienda (Row Level Security).
- I **ruoli** decidono cosa può fare l'utente; la **licenza** dell'azienda decide quali funzioni
  sono incluse (es. l'assistente AI).

## Accedere alla console di Keycloak

- Produzione: `https://<dominio>/sso/admin` · sviluppo: <http://localhost:8180/admin>
- In alto a sinistra scegli il realm **gestionale** (non *master*, che serve solo agli amministratori
  di Keycloak).

L'utente `admin` del realm *master* (password nel `.env`) resta: lo usa il servizio `keycloak-setup`
per allineare la configurazione. Gli amministratori della piattaforma stanno nel realm **piattaforma**
([console-piattaforma.md](console-piattaforma.md#primo-accesso-una-volta-sul-server-di-produzione)).

## Utenti

Dalla console di piattaforma: pagina dell'azienda → *Utenti → Nuovo utente* (il `tenant_id` si
assegna da solo). A mano, nella console di Keycloak:

### Aggiungere un utente

1. **Users → Add user**: username (es. `mario.rossi`), email, nome, cognome. Spunta *Email verified*
   se l'indirizzo è sicuro. **Create**.
2. Scheda **Attributes**: `tenant_id` = l'id dell'azienda (vedi «Aziende» sotto). **Save**.
   Senza `tenant_id` l'utente entra ma non vede dati.
3. Scheda **Role mapping → Assign role → Filter by realm roles**: scegli **un** ruolo tra `admin`,
   `operator`, `viewer` (ruoli d'azienda) oppure `capocantiere`, `caposquadra`, `operaio`,
   `amministrativo` (ruoli di cantiere). **Assign**.
4. Scheda **Credentials → Set password**: password provvisoria con **Temporary** attivo: al primo
   accesso l'utente la cambia.
   In alternativa, con l'SMTP configurato in *Realm settings → Email*: scheda **Details → Credential
   reset / Send email** con l'azione *Update Password*: l'utente sceglie la password da solo.

### Operazioni frequenti

| Cosa | Dove |
|------|------|
| Password dimenticata | l'utente usa «Password dimenticata?» nella pagina di accesso (serve l'SMTP), oppure *Credentials → Reset password* |
| Utente bloccato dopo troppi tentativi | *Users →* utente *→* interruttore **Enabled** / messaggio «temporarily locked» → sblocca |
| Sospendere un utente (es. uscito dall'azienda) | *Details →* **Enabled** spento: non può più accedere, i dati restano |
| Cambiare ruolo | *Role mapping*: togli il vecchio (*Unassign*), assegna il nuovo. Vale dal prossimo accesso |
| Chi ha fatto accesso e quando | *Events → User events* (conservati 90 giorni) |
| Disconnettere un utente subito | *Users →* utente *→ Sessions → Sign out* |
| Autenticazione a due fattori | *Details → Required user actions → Configure OTP* (app come Google Authenticator) |

## Ruoli

| Ruolo | Etichetta nell'app | Permessi |
|-------|--------------------|----------|
| `admin` | Amministratore | tutto: clienti, comunicazioni ai dipendenti, assistente, gestione utenti. È anche il manager/proprietà dell'azienda |
| `operator` | Operatore | consultare e modificare i clienti, usare l'assistente |
| `viewer` | Consultazione | consultare i clienti |
| `capocantiere` | Capocantiere | ruolo di cantiere: verifica e conferma il lavoro delle squadre (permessi aggiunti via via che le funzioni di cantiere vengono costruite, vedi `docs/roadmap-alex.md`) |
| `caposquadra` | Caposquadra | ruolo di cantiere: coordina la propria squadra (idem) |
| `operaio` | Operaio | ruolo di cantiere: lavora sul cantiere, inserisce ore e rapportini (idem) |
| `amministrativo` | Amministrativo | ruolo di cantiere, accesso limitato: materiali, personale, attrezzature, fornitori, archivio — mai rapportini, contabilità o pianificazione (idem) |

I permessi esatti di ogni ruolo sono in `Role` (backend, `apps/api/.../auth/Role.java`): Keycloak
assegna solo il ruolo, il significato lo decide l'applicazione (così il provider di identità resta
sostituibile). I quattro ruoli di cantiere sono scaffolding senza permessi propri finché non esiste
la funzione da proteggere: ogni voce di `docs/roadmap-alex.md` che introduce un endpoint nuovo
aggiunge i `Permission` giusti ai ruoli giusti.

### Aggiungere un nuovo ruolo

È una **modifica al software**, non solo a Keycloak, perché il backend deve sapere cosa permette:

1. `infra/keycloak/realm/gestionale-realm.json`: aggiungi il ruolo in `roles.realm`.
2. `apps/api/.../auth/Role.java`: nuovo valore con i suoi permessi (`Permission`).
3. `apps/web/src/app/core/profile/profile.service.ts`: etichetta in `ROLE_LABELS`.
4. Documentazione: questa tabella e `infra/keycloak/README.md`.
5. Nelle installazioni già avviate il realm non si reimporta: crea il ruolo anche dalla console
   (*Realm roles → Create role*, stesso nome).

Lo stesso vale per un nuovo **permesso** su un ruolo esistente (solo passi 2–4).

## Aziende (tenant)

Dalla console di piattaforma: *Aziende → Nuova azienda*, poi licenza, sospensione e riattivazione
nella pagina dell'azienda. Le procedure SQL qui sotto restano per emergenze e per la chiusura
definitiva.

Ogni azienda cliente è una riga in `tenants` con una licenza in `licenses`. L'azienda demo
(`00000000-0000-7000-8000-00000000de00`) la crea l'API da sola se `DEMO_ENABLED=true`.

### Creare un'azienda

Sulla macchina di produzione, dalla cartella `/opt/civilbeefy`, apri il database come amministratore:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d gestionale'
```

Poi, sostituendo nome, sigla e licenza:

```sql
WITH nuova AS (
  INSERT INTO tenants (id, name, slug, kind)
  VALUES (gen_random_uuid(), 'Rossi S.r.l.', 'rossi', 'customer')
  RETURNING id
)
INSERT INTO licenses (id, tenant_id, plan, max_users, features, ai_quota_monthly, valid_from, valid_until)
SELECT gen_random_uuid(), id, 'standard', 50, '[]'::jsonb, 0, now(), NULL
FROM nuova
RETURNING tenant_id;
```

- `slug`: sigla unica, minuscola, senza spazi.
- `features`: funzioni incluse, es. `'["aiAssistant"]'::jsonb` per l'assistente AI (con
  `ai_quota_monthly` = richieste al mese).
- `valid_until`: `NULL` = senza scadenza, oppure es. `'2027-12-31'`.
- Il comando stampa l'**id dell'azienda** (`tenant_id`): è il valore da mettere nell'attributo
  `tenant_id` dei suoi utenti in Keycloak. Esci con `\q`.

Elenco delle aziende: `SELECT id, name, slug, status FROM tenants ORDER BY name;`

### Cambiare piano, sospendere, chiudere

```sql
-- Aggiungere l'assistente AI
UPDATE licenses SET features = '["aiAssistant"]'::jsonb, ai_quota_monthly = 500 WHERE tenant_id = '<id>';

-- Sospendere (es. mancato pagamento). L'app non blocca ancora l'accesso in base allo stato:
-- disattiva anche gli utenti dell'azienda in Keycloak (Enabled spento)
UPDATE tenants SET status = 'suspended' WHERE id = '<id>';

-- Chiudere a fine contratto: cancella TUTTI i dati dell'azienda (fai prima un backup / un'esportazione)
DELETE FROM tenants WHERE id = '<id>';
```

Dopo la chiusura elimina anche gli utenti dell'azienda in Keycloak (*Users*, cerca per attributo
`tenant_id`). Per gli aspetti GDPR vedi [privacy-gdpr.md](privacy-gdpr.md).

### Un amministratore per azienda

Un utente con ruolo `admin` è amministratore **della propria azienda** nell'app (comunicazioni ai
dipendenti, ecc.), non dell'installazione. Gli utenti in Keycloak però li crea chi gestisce
l'installazione: l'amministratore di un'azienda non ha accesso alla console di Keycloak, perché
vedrebbe anche gli utenti delle altre aziende.

### In futuro

- Per i clienti più grandi: un **realm** o un'**installazione dedicata** per cliente,
  con il loro amministratore e, se serve, l'accesso con l'account aziendale (Microsoft Entra ID,
  Google Workspace) tramite *Identity providers* di Keycloak.
