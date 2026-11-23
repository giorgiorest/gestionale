# gestionale

Gestionale multi-piattaforma e multi-tenant: un'unica base di codice per **web** (PWA),
**mobile** (Ionic/Capacitor) e **desktop** (Tauri), con funzionamento **offline** e
sincronizzazione verso un backend **Spring Boot** su **PostgreSQL**. Login e profili utente
con **Keycloak** (OIDC).

Visione e scelte tecniche: [docs/architettura.md](docs/architettura.md) e [docs/decisioni/](docs/decisioni/).

## Struttura

```
gestionale/
├── .github/workflows/   # CI (api, web) e deploy manuale (placeholder)
├── apps/
│   ├── api/             # Spring Boot 3.5, Java 21, Maven
│   ├── web/             # Angular 22 + Ionic 9, PWA, Dexie (offline), oidc-client-ts
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
| `db/`                      | `LocalDb` (Dexie): `outbox`, `customers`, `syncState` |
| `features/`                | `auth` (accesso), `server` (app desktop), `home`, `profile`, `customers`, `assistant` |
| `shared/`                  | utilità (UUID v7) |

## Prerequisiti

- Git
- Java 21 (JDK) — Maven non serve, c'è il wrapper (`mvnw` / `mvnw.cmd`)
- Node.js 22 e npm
- Un motore di container: **Docker** oppure **Podman** (per PostgreSQL e Keycloak in sviluppo,
  e per i test di integrazione con Testcontainers)
- Per l'app desktop: Rust e Microsoft C++ Build Tools (vedi `apps/desktop/README.md`)
- Facoltativo: GitHub CLI (`gh`)

## Avvio locale (sviluppo)

Quattro processi: database, Keycloak, API, web. I comandi sono per PowerShell/bash; con Docker
sostituisci `podman` con `docker`. Su Windows e macOS, con Podman, prima `podman machine start`.

**1. Database** (PostgreSQL 17), dalla radice del repository:

```bash
podman run -d --name gestionale-db -p 5432:5432 -e POSTGRES_DB=gestionale -e POSTGRES_USER=gestionale -e POSTGRES_PASSWORD=gestionale -e DB_USER=gestionale_app -e DB_PASSWORD=gestionale_app -v ./infra/docker/postgres-init:/docker-entrypoint-initdb.d:ro,Z docker.io/library/postgres:17
```

Lo script in `postgres-init` crea `gestionale_app`, il ruolo (non superuser) con cui si collega l'API.
Le password coincidono con i default del profilo `dev`.

**2. Keycloak**, dalla radice del repository:

```bash
podman run -d --name gestionale-keycloak -p 8180:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin -e GESTIONALE_WEB_URL=http://localhost:4200 -v ./infra/keycloak/realm/gestionale-realm.json:/opt/keycloak/data/import/gestionale-realm.json:ro,Z -v ./infra/keycloak/dev/gestionale-users-0.json:/opt/keycloak/data/import/gestionale-users-0.json:ro,Z quay.io/keycloak/keycloak:26.8.2 start-dev --import-realm
```

**3. API** (applica le migrazioni e crea il tenant demo):

```bash
cd apps/api
./mvnw spring-boot:run          # su Windows/PowerShell: .\mvnw.cmd spring-boot:run
```

**4. Web**:

```bash
cd apps/web
npm ci
npm start
```

Apri <http://localhost:4200> e accedi con un utente demo (password `demo-password`):

| Utente | Ruolo | Cosa vede |
|--------|-------|-----------|
| `admin.demo` | Amministratore | tutto |
| `operatore.demo` | Operatore | clienti (lettura e inserimento), assistente |
| `lettore.demo` | Consultazione | clienti in sola lettura |

`npm start` inoltra `/api` all'API su `localhost:8080` (`apps/web/proxy.conf.json`), quindi non
serve configurare CORS. La configurazione della web app in sviluppo è in `apps/web/public/config.json`.

### Accesso al database con un client SQL

Host `127.0.0.1`, porta `5432`, database `gestionale`, utente `gestionale`, password `gestionale`.
Con l'utente `gestionale_app` la Row Level Security nasconde tutto finché non imposti il tenant:
`SET app.tenant_id = '00000000-0000-7000-8000-00000000de00';` (tenant demo).

### Problemi frequenti

- **Container del database in stato `Exited (127)`** con «required file not found» nei log:
  lo script `.sh` ha fine riga Windows (CRLF). Il `.gitattributes` del repository lo impedisce;
  se la copia era stata scaricata prima, riallinea i file: `git rm -r --cached . -q` seguito da
  `git reset --hard` (attenzione: scarta le modifiche non committate), poi ricrea il container.
- **Pagina di accesso con «Servizio di accesso non raggiungibile»**: Keycloak non è avviato
  o non è su `localhost:8180`.
- **Dopo il login torna un errore di redirect di Keycloak**: `GESTIONALE_WEB_URL` di Keycloak
  non coincide con l'indirizzo della web app (4200 con `npm start`, 8081 con compose).

## Stack completo con compose

```bash
cp infra/docker/.env.example infra/docker/.env   # poi impostare le password
docker compose -f infra/docker/compose.yml up --build
```

Con Podman: `podman compose -f infra/docker/compose.yml up --build`.

| Servizio | URL |
|----------|-----|
| web (nginx) | <http://localhost:8081> (proxy `/api` verso l'API) |
| api | <http://localhost:8080/api/health> |
| keycloak | <http://localhost:8180/admin> |
| db | `localhost:5432` |

L'immagine web genera `config.json` all'avvio dalle variabili `WEB_API_URL`, `OIDC_AUTHORITY`,
`OIDC_CLIENT_ID`: la stessa immagine si installa presso clienti diversi senza ricompilare.

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

## Utenti, ruoli e permessi

- Utenti e ruoli si gestiscono in Keycloak (realm `gestionale`); ogni utente ha l'attributo
  `tenant_id` (azienda). Guida: [infra/keycloak/README.md](infra/keycloak/README.md).
- Ogni ruolo concede dei permessi, definiti nel backend (`apps/api/.../auth/Role.java`):

| Permesso | admin | operator | viewer |
|----------|:-----:|:--------:|:------:|
| `customers:read` — consultare i clienti | ✓ | ✓ | ✓ |
| `customers:write` — creare e modificare i clienti | ✓ | ✓ | |
| `assistant:use` — usare l'assistente (se incluso nel piano) | ✓ | ✓ | |
| `users:manage` — gestire gli utenti (area da realizzare) | ✓ | | |

## CI e deploy

- `ci-api.yml`: build e test dell'API; su `main` pubblica l'immagine su GHCR.
- `ci-web.yml`: build di produzione e test della web app.
- `ci-desktop.yml`: installer Windows dell'app desktop (artifact `gestionale-desktop-windows`, non ancora firmato).
- `deploy.yml`: manuale e volutamente bloccato finché non si sceglie il target
  ([ADR 0002](docs/decisioni/0002-deploy-target.md)).
