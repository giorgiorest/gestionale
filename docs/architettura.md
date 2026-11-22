# Architettura

## Obiettivo

Un gestionale usato da circa **50 utenti** per installazione, dove **ogni utente modifica
principalmente la propria parte** dei dati. Deve funzionare su web, mobile e desktop, anche
**senza connessione**, e servire più aziende clienti da un'unica istanza.

## Panoramica

```
 ┌──────────── Client (stesso codice Angular) ─────────────┐
 │  Web/PWA        Mobile (Ionic + Capacitor)   Desktop (Tauri) │
 │        └──────── IndexedDB (Dexie): dati + outbox ───────┘   │
 └───────────────────────────┬──────────────────────────────┘
                             │ HTTPS, JWT (claim tenant_id)
                 ┌───────────▼───────────┐
                 │  API Spring Boot      │  tenancy · licensing · ai · domini
                 └───────────┬───────────┘
                             │ app.tenant_id per connessione
                 ┌───────────▼───────────┐
                 │  PostgreSQL + RLS     │  Flyway per le migrazioni
                 └───────────────────────┘
```

## Client

- **Una sola web app Angular** in `apps/web`, con componenti **Ionic**.
  - **Web**: PWA con service worker (`@angular/pwa`), installabile e utilizzabile offline.
  - **Mobile**: la stessa build impacchettata con **Capacitor** (iOS, Android).
  - **Desktop**: la stessa build incapsulata da **Tauri 2** (`apps/desktop`).
- Il frontend nasconde la UI non inclusa nel piano (`*appHasFeature`, `featureGuard`),
  ma **non è una protezione**: ogni controllo vale solo se ripetuto nel backend.

## Offline e sincronizzazione

- **ID generati dal client**: UUID v7 (ordinabili nel tempo), così un record creato offline
  ha già il suo id definitivo e non servono rimappature.
- **Outbox**: ogni scrittura locale aggiorna il dato in IndexedDB e accoda un'operazione
  (`entity`, `operation`, `payload`, `createdAt`, `attempts`) nella tabella `outbox`.
  I componenti non chiamano direttamente le API di scrittura: passano da `SyncService.enqueue()`.
- **Invio (`flush`)**: la outbox viene inviata in ordine di creazione; le voci confermate
  vengono rimosse, gli errori incrementano `attempts` con backoff. Le operazioni devono essere
  idempotenti lato server (stesso id = stessa operazione).
- **Ricezione (`pullChanges`)**: sincronizzazione incrementale con **cursore** per entità
  (`syncState`): il client chiede le modifiche successive all'ultimo cursore ricevuto
  (es. `updated_at` + id, o una sequenza di modifica) e le applica localmente.
- **Conflitti**: con 50 utenti che modificano soprattutto la propria parte, i conflitti sono
  rari. Strategia iniziale: *last-write-wins* per record, con `updated_at` lato server;
  i casi critici (es. giacenze) andranno gestiti con operazioni di dominio invece che con
  sovrascritture di campi.

## Backend

- **Spring Boot 3.5**, Java 21, API REST stateless, autenticazione **OAuth2/OIDC** (JWT).
- **PostgreSQL 17** con migrazioni **Flyway** (mai modificare una migrazione già applicata).
- Errori in formato RFC 9457 (`application/problem+json`).

## Multi-tenant

- Ogni tenant è un'**azienda cliente** (`kind=customer`) o un **tenant demo** (`kind=demo`).
- Ogni tabella di dominio ha `tenant_id UUID NOT NULL` con FK verso `tenants` e indice.
- **Row Level Security** su PostgreSQL, abilitata e forzata con `CALL enable_tenant_rls('tabella')`:
  la policy confronta `tenant_id` con `current_tenant_id()`, che legge `app.tenant_id`.
- Catena della richiesta:
  1. `TenantFilter` legge il claim `tenant_id` dal JWT e lo mette in `TenantContext`;
  2. `TenantAwareDataSource` imposta `app.tenant_id` su ogni connessione presa dal pool
     (stringa vuota se manca il tenant: nessuna riga visibile, *fail-closed*);
  3. le policy RLS filtrano letture e bloccano scritture su altri tenant.
- L'applicazione si collega con un **ruolo non superuser** e non proprietario delle tabelle
  (`gestionale_app`, senza `BYPASSRLS`); le migrazioni usano un utente distinto.
- Le query applicative filtrano comunque per `tenant_id` (difesa in profondità).

## Tenant demo

- `DemoTenantSeeder` crea il tenant demo con licenza e dati di esempio quando
  `gestionale.demo.enabled=true` (default nel profilo `dev`, mai implicito in `prod`).
- `reset()` cancella i dati del tenant demo e ricarica quelli iniziali (da esporre a un
  job pianificato o a un endpoint amministrativo).

## Licenze ed entitlement

- Gli **entitlement** (piano, utenti massimi, funzionalità, quota AI, scadenza) sono
  commerciali e distinti dalle feature flag tecniche.
- Online: letti dalla tabella `licenses` del tenant tramite `EntitlementService`.
- Offline: **licenza firmata** (Ed25519) `base64url(payload).base64url(firma)`, verificata
  con la sola chiave pubblica (`LicenseVerifier`). La chiave privata resta fuori dal
  repository e dalle app distribuite; la generazione delle licenze è un processo separato.

## Assistente AI

- Chat nel frontend (`features/assistant`), disponibile solo con la funzionalità `aiAssistant`.
- Backend provider-agnostico: `AiProvider` (implementazione di default `NoopAiProvider`, 503)
  e `AiTool` per le funzioni invocabili dal modello.
- I tool sono wrapper sottili sui servizi di dominio, eseguiti **con i permessi dell'utente
  e nel tenant corrente** (quindi sotto RLS). Le scritture richiedono **conferma esplicita**.
- Quota mensile da `aiQuotaMonthly`; ogni chiamata registra tenant, utente, modello, token e
  costo stimato (`ai_usage`).
- Ai provider si inviano solo i dati necessari al task, e solo se il tenant ha un contratto di
  elaborazione dati e la configurazione lo consente.
