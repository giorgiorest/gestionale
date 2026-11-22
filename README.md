# gestionale

Gestionale multi-piattaforma e multi-tenant: un'unica base di codice per **web** (PWA),
**mobile** (Ionic/Capacitor) e **desktop** (Tauri), con funzionamento **offline** e
sincronizzazione verso un backend **Spring Boot** su **PostgreSQL**.

Visione e scelte tecniche: [docs/architettura.md](docs/architettura.md) e [docs/decisioni/](docs/decisioni/).

## Struttura

```
gestionale/
├── .github/workflows/   # CI (api, web) e deploy manuale (placeholder)
├── apps/
│   ├── api/             # Spring Boot 3.5, Java 21, Maven
│   ├── web/             # Angular 22 + Ionic 9, PWA, Dexie (offline)
│   └── desktop/         # Tauri 2 (solo istruzioni, da inizializzare)
├── infra/docker/        # compose per lo sviluppo locale, Dockerfile web, nginx
├── docs/
│   ├── architettura.md
│   └── decisioni/       # ADR
├── CLAUDE.md            # regole per le sessioni di Claude Code
└── README.md
```

### Backend (`apps/api/src/main/java/it/gestionale/api`)

| Modulo       | Contenuto |
|--------------|-----------|
| `tenancy/`   | `TenantContext`, `TenantFilter` (tenant dal JWT), `TenantAwareDataSource` (imposta `app.tenant_id` sulla connessione) |
| `licensing/` | `Entitlements`, `EntitlementService`, `LicenseVerifier` (licenze offline Ed25519), `GET /api/entitlements` |
| `ai/`        | `AiProvider`, `AiTool`, `NoopAiProvider` (503), quota mensile, `POST /api/ai/chat` |
| `demo/`      | `DemoTenantSeeder` con `reset()` |
| `customers/` | prima tabella di dominio d'esempio |
| `health/`    | `GET /api/health` |

### Frontend (`apps/web/src/app`)

| Cartella                   | Contenuto |
|----------------------------|-----------|
| `core/api`, `core/auth`    | URL dell'API, interceptor del bearer token |
| `core/sync`                | `SyncService` (outbox: `enqueue`, `flush`, `pullChanges`) |
| `core/entitlements`        | servizio entitlement, direttiva `*appHasFeature`, `featureGuard` |
| `db/`                      | `LocalDb` (Dexie): `outbox`, `customers`, `syncState` |
| `features/assistant`       | chat placeholder, protetta da `aiAssistant` |
| `shared/`                  | utilità (UUID v7) |

## Prerequisiti

- Git
- Java 21 (JDK) — Maven non serve, c'è il wrapper `mvnw`
- Node.js 22 e npm
- Docker con Docker Compose (stack locale e test di integrazione con Testcontainers)
- Facoltativi: GitHub CLI (`gh`); Rust per il desktop (vedi `apps/desktop/README.md`)

## Stack locale

```bash
cp infra/docker/.env.example infra/docker/.env   # poi impostare le password
docker compose -f infra/docker/compose.yml up --build
```

| Servizio | URL |
|----------|-----|
| web (nginx) | <http://localhost:8081> (proxy `/api` verso l'API) |
| api | <http://localhost:8080/api/health> |
| db | `localhost:5432` |

Con il profilo `dev` l'API crea il tenant demo (`00000000-0000-7000-8000-00000000de00`).
Il ruolo applicativo del database viene creato da `infra/docker/postgres-init/` al primo
avvio del volume; per ricrearlo: `docker compose -f infra/docker/compose.yml down -v`.

## Build e test

### API

```bash
cd apps/api
./mvnw -B verify          # build + test (richiede Docker per Testcontainers)
./mvnw spring-boot:run    # avvio con profilo dev (serve PostgreSQL su localhost:5432)
```

### Web

```bash
cd apps/web
npm ci
npm start                 # sviluppo su http://localhost:4200
npm run build             # build di produzione in dist/web/browser
npm test -- --watch=false # test unitari (Vitest)
```

### Desktop

Non ancora inizializzato: vedi [apps/desktop/README.md](apps/desktop/README.md).

## CI e deploy

- `ci-api.yml`: build e test dell'API; su `main` pubblica l'immagine su GHCR.
- `ci-web.yml`: build di produzione e test della web app.
- `deploy.yml`: manuale e volutamente bloccato finché non si sceglie il target
  ([ADR 0002](docs/decisioni/0002-deploy-target.md)).
