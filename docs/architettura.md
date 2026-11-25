# Architettura di Civil Beefy

## Obiettivo

Il gestionale delle **imprese edili e di cantiere** ([ADR 0012](decisioni/0012-verticale-edilizia.md)):
la commessa è l'entità centrale e il rapportino giornaliero, compilato dal telefono in cantiere, è
il documento più usato.

Usato da circa **50 utenti** per installazione, dove **ogni utente modifica principalmente la
propria parte** dei dati. Deve funzionare su web, mobile e desktop, anche **senza connessione** —
in cantiere il campo non c'è, e una funzionalità che richiede la rete è una funzionalità incompleta —
e servire più aziende clienti da un'unica istanza.

Lo stato del dominio e il lavoro da fare, diviso in pull request: [roadmap.md](roadmap.md).

## Panoramica

```
┌────────────── Client (stesso codice Angular) ──────────────┐
│  Web/PWA      Desktop (Tauri)      Android (Tauri)         │
│        IndexedDB (Dexie): dati locali + outbox             │
└───────────────┬───────────────────────────────┬────────────┘
                │ login OIDC (code + PKCE)      │ HTTPS + JWT
                ▼                               │ (tenant_id, roles, aud)
      ┌──────────────────┐                      ▼
      │  Keycloak        │  chiavi   ┌───────────────────────┐
      │  realm           │──────────▶│  API Spring Boot      │ auth · tenancy · licensing
      │  "gestionale"    │  (JWKS)   │                       │ ai · domini
      └──────────────────┘           └──────────┬────────────┘
                                                │ app.tenant_id per connessione
                                     ┌──────────▼────────────┐
                                     │  PostgreSQL + RLS     │ Flyway per le migrazioni
                                     └───────────────────────┘
```

## Client

- **Una sola web app Angular** in `apps/web`, con componenti **Angular Material** e layout per il PC
  (barra superiore e menu laterale; [ADR 0006](decisioni/0006-interfaccia-angular-material.md)).
- **Marchio**: tema Material 3 di prodotto; nome, logo e colore principale personalizzabili per
  installazione da `config.json` (`BrandingService`). Font e icone inclusi nell'app.
  - **Web**: PWA con service worker (`@angular/pwa`), installabile e utilizzabile offline.
  - **Desktop**: la stessa build incapsulata da **Tauri 2** (`apps/desktop`), installer Windows.
  - **Android**: la stessa build e lo stesso progetto Tauri, pacchettizzati come APK firmato
    ([ADR 0011](decisioni/0011-app-android.md)) e distribuito dalla pagina `/app-desktop` del
    server, come gli installer Windows. Desktop e Android condividono il modo di riconoscersi
    (`isPackagedDesktopApp()`), la scelta del server al primo avvio e il client OIDC
    `gestionale-desktop`. iOS non è previsto.
- Il frontend nasconde la UI non inclusa nel piano (`*appHasFeature`, `featureGuard`) o non
  consentita dal ruolo (`*appHasPermission`, `permissionGuard`), ma **non è una protezione**:
  ogni controllo vale solo se ripetuto nel backend.
- **Configurazione a runtime**: URL dell'API e del provider di identità arrivano da `config.json`
  caricato all'avvio (in Docker è generato dalle variabili d'ambiente del container). La stessa
  build serve quindi qualsiasi installazione o cliente; il service worker conserva l'ultima
  configurazione per l'avvio offline.

## App desktop

Dettagli: [ADR 0004](decisioni/0004-app-desktop.md) e `apps/desktop/README.md`.

- Tauri 2 incapsula la build web; l'app riconosce a runtime di girare nell'app installata.
- **Distribuzione**: gli installer (setup `.exe` e MSI) sono costruiti da *Release immagini* e inclusi
  nell'immagine web dello stesso commit; ogni server li offre alla pagina pubblica `/app-desktop`,
  che mostra anche l'indirizzo del server da inserire al primo avvio.
- **Un installer per tutti i clienti**: al primo avvio l'utente indica l'indirizzo del server,
  l'app scarica `config.json` da lì e conserva l'ultima copia per l'avvio offline.
- Login con il client Keycloak `gestionale-desktop`; l'API accetta le origini di Tauri via CORS.

## Offline e sincronizzazione

- **ID generati dal client**: UUID v7 (ordinabili nel tempo), così un record creato offline
  ha già il suo id definitivo e non servono rimappature.
- **Outbox**: ogni scrittura locale aggiorna il dato in IndexedDB e accoda un'operazione
  (`entity`, `operation`, `payload`, `createdAt`, `attempts`) nella tabella `outbox`.
  I componenti non chiamano direttamente le API di scrittura: passano da `SyncService.enqueue()`.
- **Invio (`flush`, implementato)**: la outbox viene inviata in ordine di creazione con
  `PUT /api/{entità}/{id}` (creazione o modifica) e `DELETE /api/{entità}/{id}`; le voci confermate
  vengono rimosse. Errori di rete, 5xx, 401, 408 e 429 fermano l'invio (si riprova al ritorno
  della connessione o al successivo caricamento); gli altri 4xx sono errori definitivi: la voce
  resta in coda con `lastError` e l'invio prosegue. Le API di scrittura sono **idempotenti**
  (stesso id = stessa operazione), così un reinvio non crea duplicati: anche il `DELETE` risponde
  `204` se il record non esiste o era già cancellato, così una voce non resta bloccata in coda.
- **Cancellazione logica** ([ADR 0018](decisioni/0018-cancellazione-logica.md)): `DELETE` valorizza
  `deleted_at` e lascia la riga in tabella come **tombstone**; le letture escludono i record
  cancellati. È l'unico modo di dire a un dispositivo rimasto offline che un record non esiste più:
  con una cancellazione fisica non resterebbe niente da sincronizzare. Un `PUT` successivo riattiva
  il record (last-write-wins), senza toccare `created_at`/`created_by`.
- **Chi ha scritto**: ogni tabella di dominio ha `created_by`/`updated_by` (claim `sub`; NULL per i
  processi di sistema) e `version` per il blocco ottimistico (`@Version`); un conflitto diventa
  `409`.
- **Lettura**: per ora ogni pagina scarica l'elenco completo e lo salva in IndexedDB; se il
  server non risponde mostra la copia locale (esempio: `features/customers`).
- **Ricezione (`pullChanges`, da implementare)**: sincronizzazione incrementale con **cursore** per entità
  (`syncState`): il client chiede le modifiche successive all'ultimo cursore ricevuto
  (es. `updated_at` + id, o una sequenza di modifica) e le applica localmente.
- **Conflitti**: con 50 utenti che modificano soprattutto la propria parte, i conflitti sono
  rari. Strategia iniziale: *last-write-wins* per record, con `updated_at` lato server;
  la colonna `version` c'è già, il controllo con `If-Match` e la risoluzione nell'interfaccia sono
  la voce 1.5 della roadmap. I casi critici (es. giacenze) andranno gestiti con operazioni di
  dominio invece che con sovrascritture di campi.

## Backend

- **Spring Boot 3.5**, Java 21, API REST stateless, autenticazione **OAuth2/OIDC** (JWT emessi da Keycloak).
- **PostgreSQL 17** con migrazioni **Flyway** (mai modificare una migrazione già applicata).
- Errori in formato RFC 9457 (`application/problem+json`).

## Identità e profilazione

Dettagli e motivazioni (anche per la rivendita): [ADR 0003](decisioni/0003-identita-keycloak.md).

- **Keycloak 26**, realm `gestionale` versionato in `infra/keycloak/realm` (configurazione come codice).
- La web app usa il flusso **Authorization Code + PKCE** con `oidc-client-ts` (client pubblico
  `gestionale-web`, nessun segreto nell'app); il token viaggia come `Authorization: Bearer`.
- L'API valida firma, issuer e audience (`gestionale-api`) e legge solo claim neutri:
  `sub`, `preferred_username`, `name`, `email`, `tenant_id`, `roles`.
- **Profilazione**: in Keycloak si assegnano ruoli d'azienda (`admin`, `operator`, `viewer`) e di
  cantiere (`capocantiere`, `caposquadra`, `operaio`, `amministrativo`, `docs/roadmap-alex.md`); il
  backend li traduce in permessi (`customers:read`, `customers:write`, `resources:read`,
  `resources:write`, `commesse:read`, `commesse:write`, `resource-assignments:read`,
  `resource-assignments:write`, `work-activities:read`, `work-activities:write`,
  `daily-reports:read`, `daily-reports:write`, `assistant:use`, `users:manage`) controllati con
  `@PreAuthorize`. `GET /api/me` restituisce profilo, tenant, ruoli
  e permessi.
- Accesso a una funzione = **ruolo** dell'utente (permesso) **e** **piano** del tenant (entitlement).
- L'attributo `tenant_id` lo modificano solo gli amministratori di Keycloak.

## Console di piattaforma

Dettagli: [ADR 0010](decisioni/0010-console-piattaforma.md) e [guida](console-piattaforma.md).

```
amministratore ── realm "piattaforma" (password + OTP) ──▶ /console ──▶ /api/platform/** (catena propria)
                                                                          │ app.worker = platform-admin
                                                                          ├─▶ tenants, licenses, platform_audit (RLS dedicata)
                                                                          └─▶ IdentityAdmin ─▶ Keycloak Admin API
                                                                               (client di servizio: solo utenti del realm gestionale)
```

- Token accettati solo dal realm `piattaforma` con ruolo `platform-admin`; i token delle aziende sono rifiutati.
- I dati di dominio delle aziende restano invisibili alla console; ogni modifica va in `platform_audit`.
- Un'azienda sospesa viene bloccata nel `TenantFilter`; il limite di utenti della licenza è applicato.
- Facoltativo `ADMIN_ALLOWED_IPS` in Caddy per console, login degli amministratori e console di Keycloak.

## Installazione e rilasci

Dettagli: [ADR 0002](decisioni/0002-deploy-target.md), [guida VPS](deploy-vps.md) e [guida Oracle Cloud](deploy-oracle-cloud.md).

- Produzione su un server Linux con Docker Compose, raggiunto via SSH: un VPS (es. Aruba, x86) o
  una macchina Oracle Cloud Always Free (Ampere A1, arm64). Stessi file e stesso workflow:
  Caddy (HTTPS automatico) → web, `/api` → API, `/sso` → Keycloak; PostgreSQL per app e Keycloak.
- A ogni push su `main`, *Release immagini* costruisce gli installer desktop e le immagini di API e
  web per amd64 e arm64 (`sha-<commit>`), poi richiama *Deploy* per lo stesso commit, che attende
  l'**approvazione** nell'environment `production`, controlla la macchina, installa via SSH e
  verifica la salute ([ADR 0009](decisioni/0009-rilascio-continuo-con-approvazione.md)).
- *Prepara server* configura un server Ubuntu qualsiasi (aggiornamenti, firewall, SSH solo con
  chiave, Docker, `.env` con password generate sulla macchina, backup notturno), in modo idempotente.

## Privacy

Dettagli: [ADR 0008](decisioni/0008-privacy-dati-sul-dispositivo.md) e [privacy-gdpr.md](privacy-gdpr.md).

- Solo cookie tecnici (Keycloak) e archiviazioni del browser necessarie; informativa nella pagina
  pubblica `/privacy`, link ai documenti del titolare da `config.json` (`legal`).
- Il database offline (IndexedDB) registra il proprietario `tenant_id:sub` (dal token): all'avvio si
  svuota se l'utente è cambiato; all'uscita `LogoutService` invia la outbox, chiede conferma se
  restano modifiche, poi lo svuota.
- Eventi di accesso di Keycloak conservati 90 giorni.

## Notifiche

Dettagli: [ADR 0005](decisioni/0005-notifiche.md).

- Notifiche di sistema e comunicazioni degli amministratori, a una persona, a un ruolo o a tutta
  l'azienda; una riga per destinatario con `read_at` e `acknowledged_at` (presa visione, ora del server).
- I destinatari si risolvono da una **rubrica locale** (`app_users`) aggiornata a ogni accesso dai
  claim del token, senza API del provider di identità. Al primo accesso un utente riceve le
  comunicazioni con presa visione ancora valide per la sua azienda o il suo ruolo.
- Canali: portale (campanella aggiornata ogni minuto, copia offline, letture e conferme via outbox)
  ed email (coda `email_deliveries`, invio pianificato con tentativi). In seguito: tempo reale (SSE)
  e push (Web Push, desktop, mobile).
- Il processo di invio email lavora per tutti i tenant con `app.worker = 'email-dispatcher'`:
  una policy RLS dedicata gli mostra solo la coda email.

## Multi-tenant

- Ogni tenant è un'**azienda cliente** (`kind=customer`) o un **tenant demo** (`kind=demo`).
- Ogni tabella di dominio ha `tenant_id UUID NOT NULL` con FK verso `tenants` e indice.
- **Row Level Security** su PostgreSQL, abilitata e forzata con `CALL enable_tenant_rls('tabella')`:
  la policy confronta `tenant_id` con `current_tenant_id()`, che legge `app.tenant_id`.
- Catena della richiesta:
  1. `TenantFilter` legge il claim `tenant_id` dal JWT (attributo utente in Keycloak) e lo mette in `TenantContext`;
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
