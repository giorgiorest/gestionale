# civil beefy

**civil beefy** è un gestionale multi-piattaforma e multi-tenant: un'unica base di codice per **web** (PWA),
**mobile** (Capacitor) e **desktop** (Tauri), con funzionamento **offline** e
sincronizzazione verso un backend **Spring Boot** su **PostgreSQL**. Login e profili utente
con **Keycloak** (OIDC).

Il nome del prodotto è «civil beefy»; `gestionale` resta il nome tecnico (repository, pacchetti,
realm e client Keycloak, database), che non va cambiato perché lo usano configurazioni e token.

Visione e scelte tecniche: [docs/architettura.md](docs/architettura.md) e [docs/decisioni/](docs/decisioni/).

## Struttura

```
gestionale/
├── .github/workflows/   # CI (api, web) e deploy manuale (placeholder)
├── apps/
│   ├── api/             # Spring Boot 3.5, Java 21, Maven
│   ├── web/             # Angular 22 + Angular Material, PWA, Dexie (offline), oidc-client-ts
│   └── desktop/         # Tauri 2: app desktop Windows che incapsula la build web
├── infra/
│   ├── docker/          # compose per lo sviluppo locale, Dockerfile web, nginx
│   └── keycloak/        # realm "gestionale" (configurazione come codice) e utenti demo
├── docs/
│   ├── architettura.md
│   └── decisioni/       # ADR
├── CLAUDE.md            # regole per le sessioni di Claude Code
└── README.md
```

### Backend (`apps/api/src/main/java/it/gestionale/api`)

| Modulo       | Contenuto |
|--------------|-----------|
| `auth/`      | ruoli (`admin`, `operator`, `viewer`) → permessi, `GET /api/me` (profilo utente) |
| `tenancy/`   | `TenantContext`, `TenantFilter` (tenant dal JWT), `TenantAwareDataSource` (imposta `app.tenant_id` sulla connessione) |
| `licensing/` | `Entitlements`, `EntitlementService`, `LicenseVerifier` (licenze offline Ed25519), `GET /api/entitlements` |
| `customers/` | anagrafica clienti: `GET /api/customers`, `PUT /api/customers/{id}` (idempotente) |
| `notifications/` | notifiche e comunicazioni ai dipendenti, presa visione, rubrica, invio email (`EmailDispatcher`) |
| `ai/`        | `AiProvider`, `AiTool`, `NoopAiProvider` (503), quota mensile, `POST /api/ai/chat` |
| `demo/`      | `DemoTenantSeeder` con `reset()` |
| `health/`    | `GET /api/health` |

### Frontend (`apps/web/src/app`)

| Cartella                   | Contenuto |
|----------------------------|-----------|
| `core/config`              | configurazione a runtime da `config.json` (URL API e Keycloak); nell'app desktop dal server scelto dall'utente |
| `core/auth`                | login OIDC (Authorization Code + PKCE), `authGuard`, interceptor del token |
| `core/profile`             | profilo, ruoli e permessi (`GET /api/me`), `*appHasPermission`, `permissionGuard` |
| `core/entitlements`        | funzionalità del piano, `*appHasFeature`, `featureGuard` |
| `core/sync`                | outbox: `enqueue`, `flush` (invio al server), `pullChanges` (da fare) |
| `core/notifications`       | notifiche dell'utente, campanella con contatore |
| `db/`                      | `LocalDb` (Dexie): `outbox`, `customers`, `syncState` |
| `features/`                | `auth` (accesso), `server` (app desktop), `home`, `profile`, `customers`, `notifications`, `announcements` (comunicazioni), `assistant` |
| `shared/`                  | utilità (UUID v7) |

## Prerequisiti

| Serve per | Cosa installare (Windows, PowerShell) |
|-----------|----------------------------------------|
| tutto | Git |
| database, Keycloak, email di prova | **Podman** (`winget install RedHat.Podman`, poi `podman machine init`) e **podman-compose** (`winget install Python.Python.3.13`, poi in un nuovo terminale `pip install podman-compose`) |
| API | **Java 21** (JDK), ad esempio `winget install EclipseAdoptium.Temurin.21.JDK`. Maven non serve: c'è il wrapper `mvnw.cmd` |
| web | **Node.js 22** (`winget install OpenJS.NodeJS.LTS` o la versione 22 dal sito) |
| MSI dell'app desktop | **Rust** (`winget install Rustlang.Rustup`, poi `rustup default stable-msvc`) e **Microsoft C++ Build Tools** con il carico «Sviluppo di applicazioni desktop con C++»; funzionalità facoltativa di Windows **VBSCRIPT** attiva |

Su macOS e Linux gli stessi strumenti si installano con `brew` o con i pacchetti della distribuzione.
Docker non serve: va bene anche al posto di Podman (sostituisci `podman-compose` con `docker compose`).

## Avvio locale (passo passo)

Guida completa, con la modalità «tutto in container» e i problemi frequenti:
[docs/avvio-locale.md](docs/avvio-locale.md). Qui la sequenza per sviluppare. I comandi sono per
PowerShell; su macOS/Linux usa `./mvnw` al posto di `.\mvnw.cmd`.

### 1. Servizi di supporto (database, Keycloak, email)

La prima volta, dalla radice del repository:

```bash
cd infra/docker
copy .env.example .env
```

(su macOS/Linux: `cp .env.example .env`). Poi, ogni volta (su Windows e macOS prima `podman machine start`):

```bash
cd infra/docker
podman-compose up -d
podman-compose ps
```

Devono risultare attivi `db`, `keycloak` e `mailpit`; `db-init` compare come terminato
(`Exited (0)`): crea il ruolo del database con cui si collega l'API. Keycloak è pronto dopo circa
un minuto.

### 2. API

In un nuovo terminale:

```bash
cd apps/api
.\mvnw.cmd spring-boot:run
```

Al primo avvio applica le migrazioni e crea l'azienda demo. È pronta quando il log mostra
`Started GestionaleApiApplication`; verifica: <http://localhost:8080/api/health> → `{"status":"ok"}`.

### 3. Web

In un altro terminale:

```bash
cd apps/web
npm ci
npm start
```

`npm ci` serve la prima volta e dopo ogni `git pull`. Quando compare `Local: http://localhost:4200/`
apri **<http://localhost:4200>** e accedi con un utente demo (password `demo-password`):

| Utente | Ruolo | Cosa vede |
|--------|-------|-----------|
| `admin.demo` | Amministratore | tutto, comprese le comunicazioni ai dipendenti |
| `operatore.demo` | Operatore | clienti (lettura e inserimento), assistente |
| `lettore.demo` | Consultazione | clienti in sola lettura |

Email inviate dall'app: <http://localhost:8025>. Console di Keycloak: <http://localhost:8180/admin>
(`admin` / `admin`).

`npm start` inoltra `/api` all'API su `localhost:8080` (`apps/web/proxy.conf.json`). La configurazione
della web app in sviluppo è in `apps/web/public/config.json`.

### 4. MSI dell'app desktop

Con le dipendenze della web già installate (`npm ci` in `apps/web`), da un terminale:

```bash
cd apps/desktop
npm ci
npm run build -- --bundles msi
```

La prima build richiede 10–15 minuti (compilazione Rust e download di WiX). Il file è in
`apps/desktop/src-tauri/target/release/bundle/msi/civil beefy_<versione>_x64_it-IT.msi`.
Senza `--bundles msi` viene creato anche il setup `.exe`.

All'installazione l'MSI non è ancora firmato: SmartScreen chiede *Ulteriori informazioni* →
*Esegui comunque*. Al primo avvio indica il server (in locale `http://localhost:4200`). Dettagli,
modalità sviluppo (`npm run dev`) e problemi frequenti: [apps/desktop/README.md](apps/desktop/README.md).
In alternativa l'MSI già pronto si scarica da GitHub → Actions → **CI Desktop** → artifact
`gestionale-desktop-windows`.

### Fermare tutto

`Ctrl+C` nei terminali di API e web, poi da `infra/docker`: `podman-compose down`
(con `down -v` si cancellano anche i dati del database).

### Accesso al database con un client SQL

Host `127.0.0.1`, porta `5432`, database `gestionale`, utente `gestionale`, password `gestionale`.
Con l'utente `gestionale_app` la Row Level Security nasconde tutto finché non imposti il tenant:
`SET app.tenant_id = '00000000-0000-7000-8000-00000000de00';` (tenant demo).

### Problemi frequenti

- **L'API si ferma con `password authentication failed`**: il ruolo del database non è stato creato
  o ha un'altra password. Controlla che `db-init` sia `Exited (0)` (`podman-compose ps`, log con
  `podman-compose logs db-init`) e rilancia `podman-compose up -d`: `db-init` riallinea la password al `.env`.
- **Pagina di accesso con «Servizio di accesso non raggiungibile»**: Keycloak non è avviato
  o non è su `localhost:8180`.
- **Dopo il login torna un errore di redirect di Keycloak**: `GESTIONALE_WEB_URL` di Keycloak
  non coincide con l'indirizzo della web app (`http://localhost:4200`).
- Altri casi (porte occupate, password del database, cache del browser): [docs/avvio-locale.md](docs/avvio-locale.md).

## Immagine web e configurazione

L'immagine web (`infra/docker/web.Dockerfile`, nginx) genera `config.json` all'avvio dalle variabili
`WEB_API_URL`, `OIDC_AUTHORITY`, `OIDC_CLIENT_ID` e `BRAND_*`: la stessa immagine si installa presso
clienti diversi senza ricompilare. nginx inoltra `/api` all'API con gli header `X-Forwarded-*`, che
l'API usa per riconoscere l'indirizzo pubblico.

## Build e test

### API

```bash
cd apps/api
./mvnw -B verify          # build + test (i test di integrazione richiedono Docker o Podman)
```

Con Podman, Testcontainers va puntato al socket di Podman (`DOCKER_HOST`) con
`TESTCONTAINERS_RYUK_DISABLED=true`. Senza container girano solo i test unitari:
`./mvnw test -Dtest='LicenseVerifierTest,HealthControllerTest,RoleAuthoritiesConverterTest'`.

### Web

```bash
cd apps/web
npm ci
npm run build             # build di produzione in dist/web/browser
npm test -- --watch=false # test unitari (Vitest)
```

### Desktop (Windows)

```bash
cd apps/desktop
npm install
npm run dev               # finestra desktop collegata a ng serve (sviluppo)
npm run build             # installer .msi e .exe in src-tauri/target/release/bundle/
```

L'installer è unico per tutti i clienti: al primo avvio chiede l'indirizzo del server.
Dettagli, prerequisiti e cose da fare prima della vendita (firma, aggiornamenti):
[apps/desktop/README.md](apps/desktop/README.md).

## Aspetto grafico e marchio

Interfaccia con **Angular Material** (gratuita, MIT), pensata per il PC: barra superiore con logo
e menu laterale. Scelta e alternative: [ADR 0006](docs/decisioni/0006-interfaccia-angular-material.md).

- **Logo e colori del prodotto**: `apps/web/public/branding/logo.svg` e palette generata dal
  colore principale `#0B5C8E` e di accento `#F2B300`, il giallo del logo (procedura in [apps/web/README.md](apps/web/README.md)). Il simbolo a celle
  esagonali è usato anche come icona della scheda del browser, della PWA e dell'app desktop.
- **Marchio di un cliente** senza ricompilare: `branding` in `config.json` (nome, logo, colore
  principale e di accento) o, con Docker, `BRAND_NAME`, `BRAND_LOGO_URL`, `BRAND_PRIMARY_COLOR`,
  `BRAND_ACCENT_COLOR`.
- Font (Inter) e icone (Material Symbols) sono inclusi nell'app: niente Google Fonts.

## Utenti, ruoli e permessi

- La pagina di login di Keycloak usa il tema `gestionale`, con logo, colore e font dell'applicazione
  (`infra/keycloak/themes/gestionale`; tema per un cliente: [infra/keycloak/README.md](infra/keycloak/README.md)).
- Utenti e ruoli si gestiscono in Keycloak (realm `gestionale`); ogni utente ha l'attributo
  `tenant_id` (azienda). Guida: [infra/keycloak/README.md](infra/keycloak/README.md).
- Ogni ruolo concede dei permessi, definiti nel backend (`apps/api/.../auth/Role.java`):

| Permesso | admin | operator | viewer |
|----------|:-----:|:--------:|:------:|
| `customers:read` — consultare i clienti | ✓ | ✓ | ✓ |
| `customers:write` — creare e modificare i clienti | ✓ | ✓ | |
| `assistant:use` — usare l'assistente (se incluso nel piano) | ✓ | ✓ | |
| `notifications:send` — inviare comunicazioni ai dipendenti e vederne la presa visione | ✓ | | |
| `users:manage` — gestire gli utenti (area da realizzare) | ✓ | | |

## Notifiche e comunicazioni

Dettagli: [ADR 0005](docs/decisioni/0005-notifiche.md).

- **Dipendenti**: campanella con il numero di notifiche (in alto nella home), elenco in *Notifiche*;
  le comunicazioni che richiedono la **presa visione** si confermano con un pulsante e restano
  segnalate in home finché non sono confermate.
- **Amministratori**: *Comunicazioni ai dipendenti* → titolo, testo, destinatari (tutta l'azienda,
  un ruolo, una persona), presa visione, invio anche per email; per ogni comunicazione il report
  di chi l'ha letta, chi ha confermato e lo stato dell'email.
- **Sistema**: i moduli del gestionale creano notifiche con `NotificationService`.
- Le persone compaiono tra i destinatari dopo il loro primo accesso; chi entra dopo l'invio riceve
  comunque le comunicazioni con presa visione indirizzate alla sua azienda o al suo ruolo.
- Email: in sviluppo Mailpit; in produzione `EMAIL_ENABLED=true`, `SPRING_MAIL_HOST`,
  `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_FROM`, `APP_PUBLIC_URL`;
  `EMAIL_INCLUDE_BODY=false` per inviare solo titolo e link.

### Provare le notifiche in locale

1. Avvia lo stack locale ([docs/avvio-locale.md](docs/avvio-locale.md)): Mailpit è già compreso.
   Dopo aver aggiornato il codice riavvia l'API: le migrazioni creano le tabelle mancanti.
2. Per avere la comunicazione di benvenuto del tenant demo serve un database nuovo (il seeder la
   crea solo insieme al tenant): da `infra/docker`, `podman-compose --profile app down -v` e poi di
   nuovo `podman-compose up -d` (cancella i dati di prova). In alternativa salta al punto 4 e inviane una tu.
3. Accedi come `operatore.demo`: in home compaiono la campanella con il numero e il banner
   «Hai 1 comunicazione da confermare». Aprila e premi *Confermo di aver letto e compreso*.
4. In una **finestra anonima** (o un altro browser, per non condividere la sessione) accedi come
   `admin.demo` → *Comunicazioni ai dipendenti*: scegli destinatari, *Richiedi la presa visione*,
   *Invia anche per email* → *Invia*.
5. Accedi come destinatario (es. `lettore.demo` per il ruolo Consultazione) e conferma.
   Chi accede per la prima volta dopo l'invio riceve comunque le comunicazioni con presa visione.
6. Come `admin.demo` apri la comunicazione: report con lettura, presa visione ed email per persona.
7. Le email sono su <http://localhost:8025> (Mailpit); il link porta alla notifica nel portale.

La campanella si aggiorna ogni minuto: per vedere subito una notifica appena inviata ricarica la pagina.

## CI e deploy

- `ci-api.yml`: build e test dell'API.
- `ci-web.yml`: build di produzione, test e build dell'immagine web (amd64 e arm64).
- `ci-desktop.yml`: installer Windows dell'app desktop (artifact `gestionale-desktop-windows`, non ancora firmato).
- `release-images.yml`: a ogni push su `main` pubblica su GHCR le immagini di API e web
  (amd64 e arm64) con le etichette `sha-<commit>` e `latest`.
- `deploy.yml`: **manuale** (Actions → Deploy → Run workflow); installa un commit di `main` sulla
  macchina di produzione via SSH e verifica che risponda. Per tornare indietro si indica un commit precedente.

- `infra.yml` (**manuale**) e `infra-retry.yml` (ogni ora, se `OCI_RETRY_ENABLED=true`): creano con
  Terraform rete, porte, macchina e IP fisso su Oracle Cloud, riprovando finché c'è capacità
  ([infra/terraform/oracle](infra/terraform/oracle/README.md), [ADR 0007](docs/decisioni/0007-infrastruttura-terraform.md)).
- `ci-infra.yml`: formato e validità dei file Terraform.

**Produzione su un VPS** (es. Aruba, OVH; da circa 5–11 € al mese): guida passo passo in
[docs/deploy-vps.md](docs/deploy-vps.md). Il workflow Deploy controlla la macchina (Docker, `.env`,
dominio, email per i certificati) prima di installare.

**Produzione su Oracle Cloud (gratuito)**: guida passo passo in
[docs/deploy-oracle-cloud.md](docs/deploy-oracle-cloud.md); file in `infra/deploy/oracle/`
(Caddy con HTTPS automatico, PostgreSQL, Keycloak, API, web). Scelte: [ADR 0002](docs/decisioni/0002-deploy-target.md).
