# Civil Beefy

**Civil Beefy** è un gestionale multi-piattaforma e multi-tenant: un'unica base di codice per **web** (PWA),
**desktop** (Tauri, Windows) e **Android** (Tauri, APK), con funzionamento **offline** e
sincronizzazione verso un backend **Spring Boot** su **PostgreSQL**. Login e profili utente
con **Keycloak** (OIDC).

Il nome del prodotto è «Civil Beefy»; `gestionale` resta il nome tecnico (repository, pacchetti,
realm e client Keycloak, database), che non va cambiato perché lo usano configurazioni e token.

Il prodotto è rivolto alle **imprese edili e di cantiere**
([ADR 0012](docs/decisioni/0012-verticale-edilizia.md)).

Visione e scelte tecniche: [docs/architettura.md](docs/architettura.md) e [docs/decisioni/](docs/decisioni/).
Lavoro da fare, diviso in pull request: [docs/roadmap.md](docs/roadmap.md).
Mappa del codice e ricettario per aggiungere un'entità: [CLAUDE.md](CLAUDE.md).

## Struttura

```
gestionale/
├── .github/workflows/   # CI (api, web) e deploy manuale (placeholder)
├── apps/
│   ├── api/             # Spring Boot 3.5, Java 21, Maven
│   ├── web/             # Angular 22 + Angular Material, PWA, Dexie (offline), oidc-client-ts
│   └── desktop/         # Tauri 2: app Windows e Android che incapsulano la build web
├── infra/
│   ├── docker/          # compose per lo sviluppo locale, Dockerfile web, nginx
│   └── keycloak/        # realm "gestionale" (configurazione come codice) e utenti demo
├── docs/
│   ├── architettura.md
│   ├── roadmap.md       # lavoro da fare, una voce = una pull request
│   └── decisioni/       # ADR
├── .claude/
│   ├── hooks/           # hook SessionStart: contesto della sessione (branch, PR, roadmap, Flyway)
│   └── settings.json    # permessi di progetto e registrazione degli hook
├── CLAUDE.md            # regole per le sessioni di Claude Code
└── README.md
```

### Backend (`apps/api/src/main/java/it/gestionale/api`)

| Modulo       | Contenuto |
|--------------|-----------|
| `auth/`      | ruoli d'azienda (`admin`, `operator`, `viewer`) e di cantiere (`capocantiere`, `caposquadra`, `operaio`, `amministrativo`) → permessi, `GET /api/me` (profilo utente) |
| `tenancy/`   | `TenantContext`, `TenantFilter` (tenant dal JWT), `TenantAwareDataSource` (imposta `app.tenant_id` sulla connessione) |
| `licensing/` | `Entitlements`, `EntitlementService`, `LicenseVerifier` (licenze offline Ed25519), `GET /api/entitlements` |
| `customers/` | anagrafica clienti: `GET /api/customers`, `PUT /api/customers/{id}` e `DELETE /api/customers/{id}` (idempotenti; la cancellazione è logica, [ADR 0018](docs/decisioni/0018-cancellazione-logica.md)) |
| `resources/` | risorse e squadre (dipendenti che compilano i rapportini): stesso schema di `customers/`, collegate opzionalmente alla rubrica `app_users` (`userId`) e tra loro (`teamLeadId`, "fa parte della squadra di") |
| `commesse/`  | commesse (cantieri): entità centrale del prodotto ([ADR 0012](docs/decisioni/0012-verticale-edilizia.md)), con cliente (`customerId`, chiave esterna vera) e stato (`pianificata`, `in_corso`, `sospesa`, `conclusa`) |
| `resourceassignments/` | assegnazione di una risorsa a una commessa, con periodo di validità e ruolo *contestuale a quella commessa* (distinto dal ruolo applicativo della persona) |
| `workactivities/` | lavorazioni di una commessa (es. "Armatura pilastri"), riutilizzate dal rapportino per classificare le ore; `code` univoco per commessa, `parentId` opzionale per la gerarchia (WBS) |
| `dailyreports/` | testata del rapportino giornaliero (meteo, note, stato); un solo rapportino per commessa/data nell'MVP; workflow `draft → submitted → closed` con endpoint dedicati (`POST .../submit`, `.../close`, non un `PATCH` sullo stato — ADR 0019); non modificabile da nessun endpoint ordinario fuori da `draft`; notifica il manager a invio/chiusura e, con un controllo periodico (`MissingDailyReportWatchdog`, #54), se una commessa in corso resta senza rapportino in una data |
| `workentries/` | righe di ore del rapportino: risorsa, lavorazione (della stessa commessa) e ore/straordinario; il totale è derivato in UI, non salvato; non modificabili fuori da `draft`; alla chiusura, `WorkEntryCostSnapshot` congela il costo manodopera (tariffa da `ResourceAssignment.hourlyCostOverride`, NULL se assente — ADR 0019) |
| `dailyreportmaterials/` | righe di materiale del rapportino (voce 3.2 della roadmap): descrizione e unità di misura a testo libero, perché un catalogo materiali non esiste ancora (GitHub #55, `docs/roadmap-alex.md` C.1); non modificabili fuori da `draft`, stessa cancellazione logica di `workentries/`; gli allegati (foto materiali, DDT) restano rimandati a dopo la voce 2.4 (ADR 0016), per non costruire due volte l'archivio |
| `hoursreport/` | cruscotto ore di una commessa (`GET /api/commesse/{id}/reports/hours?from=&to=`, #52): tre viste (settimanale, mensile per risorsa, per lavorazione) da un'unica aggregazione per giorno/risorsa/lavorazione fatta in query; permesso riusato (`work-entries:read`), nessuna migrazione ([ADR 0020](docs/decisioni/0020-endpoint-di-report.md)) |
| `notifications/` | notifiche e comunicazioni ai dipendenti, presa visione, rubrica, invio email (`EmailDispatcher`) |
| `sync/`      | `GET /api/sync/changes`: sincronizzazione incrementale lato server, cursore per entità (`updated_at` + id) e tombstone (voce 1.2 della roadmap); `pullChanges` lato client (voce 1.3) applicherà le righe |
| `ai/`        | `AiProvider`, `AiTool`, `NoopAiProvider` (503), quota mensile, `POST /api/ai/chat` |
| `demo/`      | `DemoTenantSeeder` con `reset()` |
| `health/`    | `GET /api/health` (liveness pubblico) |
| `observability/` | Log JSON con `tenant_id`/`request_id` (`RequestLoggingContextFilter`), `GET /actuator/prometheus`, avviso email sulla salute (`HealthAlertWatchdog`) — [ADR 0022](docs/decisioni/0022-osservabilita.md) |

### Frontend (`apps/web/src/app`)

| Cartella                   | Contenuto |
|----------------------------|-----------|
| `core/config`              | configurazione a runtime da `config.json` (URL API e Keycloak); nell'app desktop dal server scelto dall'utente |
| `core/auth`                | login OIDC (Authorization Code + PKCE), `authGuard`, interceptor del token |
| `core/profile`             | profilo, ruoli e permessi (`GET /api/me`), `*appHasPermission`, `permissionGuard` |
| `core/entitlements`        | funzionalità del piano, `*appHasFeature`, `featureGuard` |
| `core/sync`                | outbox: `enqueue`, `flush` (invio al server), `pullChanges` (da fare) |
| `core/notifications`       | notifiche dell'utente, campanella con contatore |
| `db/`                      | `LocalDb` (Dexie): `outbox`, `customers`, `resources`, `commesse`, `resourceAssignments`, `workActivities`, `dailyReports`, `workEntries`, `syncState` |
| `features/`                | `auth` (accesso), `server` (app desktop), `home`, `profile`, `customers`, `resources`, `commesse`, `resource-assignments`, `work-activities`, `daily-reports`, `work-entries`, `hours-report` (cruscotto ore, nessuna copia locale: è un report, non un'entità sincronizzata), `notifications`, `announcements` (comunicazioni), `assistant` |
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
`apps/desktop/src-tauri/target/release/bundle/msi/Civil Beefy_<versione>_x64_it-IT.msi`.
Senza `--bundles msi` viene creato anche il setup `.exe`.

All'installazione l'MSI non è ancora firmato: SmartScreen chiede *Ulteriori informazioni* →
*Esegui comunque*. Al primo avvio indica il server (in locale `http://localhost:4200`). Dettagli,
modalità sviluppo (`npm run dev`) e problemi frequenti: [apps/desktop/README.md](apps/desktop/README.md).
In alternativa l'installer già pronto si scarica dalla pagina `/app-desktop` di un server installato,
oppure da GitHub → Actions → **Release immagini** (o **CI Desktop** per una PR) → artifact
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
`WEB_API_URL`, `OIDC_AUTHORITY`, `OIDC_CLIENT_ID`, `BRAND_*` e `LEGAL_*` (link a informativa privacy e
condizioni d'uso): la stessa immagine si installa presso
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

### Desktop (Windows) e Android

```bash
cd apps/desktop
npm install
npm run dev                          # finestra desktop collegata a ng serve (sviluppo)
npm run build                        # installer .msi e .exe in src-tauri/target/release/bundle/
npm run tauri -- android dev         # app Android su emulatore o telefono (sviluppo)
npm run tauri -- android build --apk # APK firmato (serve key.properties, vedi sotto)
```

I comandi Android richiedono l'SDK Android con un NDK (`ANDROID_HOME`, `NDK_HOME`) e i target
Rust `aarch64-linux-android` e simili; l'APK già pronto si scarica dalla pagina `/app-desktop`
di un server installato, oppure da GitHub → Actions → **Release immagini** (o **CI Android** per
una PR) → artifact `gestionale-android-apk`.

L'installer è unico per tutti i clienti: al primo avvio chiede l'indirizzo del server.
Gli utenti scaricano installer e APK dalla pagina **`/app-desktop`** del server («App per Windows
e Android», link nella pagina di accesso e nel menu utente):
vedi [apps/desktop/README.md](apps/desktop/README.md#distribuire-lapp-agli-utenti-pagina-di-download).
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
  `BRAND_ACCENT_COLOR`. Vale anche per le **pagine di accesso** (Keycloak sullo stesso dominio legge lo
  stesso `config.json`): nessun segno di Keycloak, solo nome, logo e colori dell'installazione.
- Font (Inter) e icone (Material Symbols) sono inclusi nell'app: niente Google Fonts.

## Utenti, ruoli e permessi

- La pagina di login di Keycloak usa il tema `gestionale`, con logo, colore e font dell'applicazione
  (`infra/keycloak/themes/gestionale`; tema per un cliente: [infra/keycloak/README.md](infra/keycloak/README.md)).
- Utenti e ruoli si gestiscono in Keycloak (realm `gestionale`); ogni utente ha l'attributo
  `tenant_id` (azienda). Guida per chi amministra (utenti, ruoli, nuove aziende e licenze):
  [docs/gestione-utenti-e-aziende.md](docs/gestione-utenti-e-aziende.md); dettagli tecnici:
  [infra/keycloak/README.md](infra/keycloak/README.md).
- Ogni ruolo concede dei permessi, definiti nel backend (`apps/api/.../auth/Role.java`):

| Permesso | admin | operator | viewer | capocantiere | caposquadra | operaio | amministrativo |
|----------|:-----:|:--------:|:------:|:------------:|:-----------:|:-------:|:---------------:|
| `customers:read` — consultare i clienti | ✓ | ✓ | ✓ | | | | |
| `customers:write` — creare, modificare e cancellare i clienti | ✓ | ✓ | | | | | |
| `assistant:use` — usare l'assistente (se incluso nel piano) | ✓ | ✓ | | | | | |
| `notifications:send` — inviare comunicazioni ai dipendenti e vederne la presa visione | ✓ | | | | | | |
| `users:manage` — gestire gli utenti (area da realizzare) | ✓ | | | | | | |
| `resources:read` — consultare le risorse e le squadre | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `resources:write` — creare, modificare e cancellare le risorse | ✓ | | | ✓ | | | ✓ |
| `commesse:read` — consultare le commesse | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `commesse:write` — creare, modificare e cancellare le commesse | ✓ | | | ✓ | | | ✓ |
| `resource-assignments:read` — consultare le assegnazioni di risorse alle commesse | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
| `resource-assignments:write` — assegnare e rimuovere risorse dalle commesse | ✓ | ✓ | | ✓ | | | |
| `work-activities:read` — consultare le lavorazioni delle commesse | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
| `work-activities:write` — creare e modificare le lavorazioni delle commesse | ✓ | ✓ | | ✓ | | | |
| `daily-reports:read` — consultare i rapportini giornalieri | ✓ | ✓ | ✓ | ✓ | ✓ | | |
| `daily-reports:write` — creare e modificare la testata di un rapportino in bozza, e inviarlo/chiuderlo (`submit`/`close`, ADR 0019) | ✓ | ✓ | | ✓ | | | |
| `work-entries:read` — consultare le ore registrate nei rapportini | ✓ | ✓ | ✓ | ✓ | ✓ | | |
| `work-entries:write` — registrare e modificare le ore nei rapportini | ✓ | ✓ | | ✓ | | | |
| `daily-report-materials:read` — consultare i materiali registrati nei rapportini | ✓ | ✓ | ✓ | ✓ | ✓ | | |
| `daily-report-materials:write` — registrare e modificare i materiali nei rapportini | ✓ | ✓ | | ✓ | | | |

`operator` e `viewer` hanno anche `resources:read` e `commesse:read` solo per raggiungere la pagina
e popolare l'elenco da cui si gestiscono le assegnazioni, le lavorazioni e i rapportini (tab
"Risorse", "Lavorazioni" e "Rapportini" della commessa): senza, i permessi su
`resource-assignments`, `work-activities`, `daily-reports` e `work-entries` sarebbero
irraggiungibili dalla UI. `operaio` non ha ancora `work-entries` (nessuna delle due): il piano lo
marca "proprie righe", cioè scrittura limitata alle righe della propria risorsa, una scelta di
prodotto non ancora presa (vedi commento su `Role.OPERAIO`).
Il piano vorrebbe `caposquadra` anche in scrittura su `daily-reports`, ma limitata alla propria
squadra: quel controllo non esiste ancora (va incrociato con `resource-assignments`), quindi per
ora `caposquadra` ha solo lettura, non la scrittura piena di `capocantiere` (vedi `Role.CAPOSQUADRA`).
`caposquadra` ha `daily-reports:write` pieno: il piano lo vorrebbe limitato alla propria squadra,
ma quella restrizione non è ancora applicata (vedi commento su `Role.CAPOSQUADRA`).

I ruoli di cantiere sono scaffolding (`docs/roadmap-alex.md`): i permessi sopra sono quelli già
costruiti; le altre funzioni di cantiere li aggiungeranno via via.

## Console di piattaforma

Su `https://<dominio>/console` chi gestisce il servizio crea le aziende, ne gestisce licenze e utenti
(password provvisoria mostrata una volta), le sospende o riattiva e consulta il registro delle
operazioni. Login separato (realm Keycloak `piattaforma`) con **OTP obbligatoria**: i dipendenti delle
aziende non possono entrarci, e l'API `/api/platform/**` rifiuta i loro token. Facoltativo:
`ADMIN_ALLOWED_IPS` limita l'accesso a reti note. Guida: [docs/console-piattaforma.md](docs/console-piattaforma.md) ·
[ADR 0010](docs/decisioni/0010-console-piattaforma.md). In locale: <http://localhost:4200/console>, utente
`admin.piattaforma` / `piattaforma-demo-2026` (al primo accesso configura l'OTP).

## Privacy e GDPR

- Solo **cookie tecnici** (accesso): nessun banner di consenso; informativa nella pagina pubblica
  `/privacy`, con i link ai documenti del titolare (`LEGAL_PRIVACY_URL`, `LEGAL_TERMS_URL`).
- I dati salvati nel browser per l'uso offline appartengono a un solo utente e si cancellano
  all'uscita o se accede un altro utente.
- Keycloak registra gli accessi (90 giorni) e le operazioni degli amministratori.
- Ruoli GDPR, obblighi di chi vende il servizio (DPA, registro, data breach), diritti degli
  interessati e cosa manca: [docs/privacy-gdpr.md](docs/privacy-gdpr.md) ·
  [ADR 0008](docs/decisioni/0008-privacy-dati-sul-dispositivo.md).

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

## Sessioni di lavoro (Claude Code)

L'hook `SessionStart` in `.claude/hooks/session-start.sh`, registrato in `.claude/settings.json`,
stampa all'avvio di ogni sessione il contesto minimo per cominciare: branch e scostamento da
`origin/main`, pull request aperta su quel branch (REST di GitHub via `gh`), prima voce non
completata di [docs/roadmap.md](docs/roadmap.md) e primo numero Flyway libero
([ADR 0013](docs/decisioni/0013-numerazione-migrazioni.md)).

Sta sotto le dieci righe di proposito: quello che stampa occupa contesto a ogni sessione. Non
installa niente, non scrive niente e non fa `fetch`; se GitHub non è raggiungibile o manca `gh`, la
riga della PR dice solo che non è stata verificata. Su `source: resume` non stampa nulla, perché la
sessione riprende con il suo contesto.

```bash
echo '{"source":"startup"}' | .claude/hooks/session-start.sh   # prova a mano
```

## CI e deploy

- `ci-api.yml`: build e test dell'API; catena di fornitura: scansione delle vulnerabilità delle
  dipendenze (Trivy, bloccante) ed elenco delle licenze (artifact).
- `ci-web.yml`: build di produzione, test e build dell'immagine web (amd64 e arm64); catena di
  fornitura: `npm audit` (bloccante), scansione dell'immagine (Trivy, solo segnalazione) ed elenco
  delle licenze (artifact).
- `dependabot.yml`: aggiornamenti settimanali di npm (`apps/web`), Maven (`apps/api`), GitHub
  Actions e delle immagini Docker di base.
- `ci-desktop.yml`: installer Windows dell'app desktop sulle pull request (artifact `gestionale-desktop-windows`,
  non ancora firmato); su `main` lo richiama `release-images.yml`.
- `android.yml`: APK Android firmato sulle pull request (artifact `gestionale-android-apk`);
  su `main` lo richiama `release-images.yml`, che lo mette nell'immagine web.
Il flusso di un rilascio:

```
merge su main ─▶ Release immagini ─┬─ desktop (installer Windows)
                                   ├─ android (APK firmato)
                                   ├─ immagini API e web (amd64, arm64; la web contiene i pacchetti)
                                   ├─ approvazione: issue «Rilascio pronto» ─▶ ⏸ commenti /deploy (entro 30 min)
                                   └─ deploy ─▶ installazione e verifica
```

L'approvazione avviene **nello stesso run**: un'issue ti avvisa (email e notifica) e il deploy parte
quando commenti `/deploy` (`/annulla` per rifiutare). I *Required reviewers* degli environment, nei
repository privati, richiedono GitHub Enterprise; questo li sostituisce. Senza risposta entro 30 minuti
la richiesta scade e si può sempre lanciare **Deploy** a mano ([ADR 0009](docs/decisioni/0009-rilascio-continuo-con-approvazione.md)).

| Workflow | Quando | Cosa fa |
|----------|--------|---------|
| `ci-api.yml` | PR e `main` (API) | build e test dell'API, scansione vulnerabilità dipendenze, elenco licenze |
| `ci-web.yml` | PR e `main` (web) | build, test, immagine web (amd64 e arm64), `npm audit`, scansione vulnerabilità immagine, elenco licenze |
| `ci-desktop.yml` | PR (web, desktop); richiamato dal rilascio | installer Windows (artifact `gestionale-desktop-windows`, non ancora firmato) |
| `android.yml` | PR (web, desktop); richiamato dal rilascio | APK Android firmato (artifact `gestionale-android-apk`) |
| `ci-workflows.yml` | PR (workflow, script, hook) | actionlint e shellcheck |
| `release-images.yml` | ogni push su `main` | installer desktop, immagini su GHCR (`sha-<commit>`, `latest`), approvazione con `/deploy` su un'issue, poi deploy |
| `deploy.yml` | dal rilascio dopo `/deploy`, o a mano | controlla la macchina, installa un commit via SSH, verifica `/api/health`; a mano anche per tornare a un commit precedente |
| `prepare-server.yml` | a mano | prepara o rimette in ordine il server: aggiornamenti, firewall, SSH, Docker, `.env`, backup |

Scelte: [ADR 0009](docs/decisioni/0009-rilascio-continuo-con-approvazione.md).

**Produzione su un VPS** (es. Aruba, OVH; da circa 5–11 € al mese): guida passo passo in
[docs/deploy-vps.md](docs/deploy-vps.md): pochi passi a mano (VPS, utente, dominio, environment
GitHub), poi **Prepara server**; a ogni rilascio approvi l'installazione commentando `/deploy`.

**Produzione su Oracle Cloud (gratuito)**: guida passo passo in
[docs/deploy-oracle-cloud.md](docs/deploy-oracle-cloud.md); file in `infra/deploy/oracle/`
(Caddy con HTTPS automatico, PostgreSQL, Keycloak, API, web). Scelte: [ADR 0002](docs/decisioni/0002-deploy-target.md).
