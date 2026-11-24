# CLAUDE.md — gestionale (prodotto: civil beefy)

Il nome del prodotto mostrato agli utenti è «civil beefy» (minuscolo, come nel logo). `gestionale` è
il nome tecnico (repository, pacchetti Java, realm e client Keycloak, database, variabili): non va
sostituito, perché lo usano configurazioni, token e installazioni esistenti.

## Stack
- Backend: Spring Boot (Java 21, Maven) in apps/api, PostgreSQL, Flyway per le migrazioni.
- Frontend: Angular con Angular Material (layout per PC), PWA per il web, Dexie per il database locale in apps/web.
- Desktop: Tauri 2 in apps/desktop, che incapsula la build web (Windows). Un solo installer per tutti i clienti: il server si sceglie al primo avvio. Installer generati da ci-desktop.yml (PR) e, su main, da release-images.yml, che li include nell'immagine web: gli utenti li scaricano dalla pagina /app-desktop del server.
- Android: stesso progetto Tauri (src-tauri/gen/android, versionato perché contiene la firma), APK firmato prodotto da android.yml (ADR 0011). Niente Capacitor. Le differenze di piattaforma stanno in tauri.android.conf.json e in CSS (env(safe-area-inset-*)), mai in una build Angular separata. Come per il desktop, su main release-images.yml mette l'APK nell'immagine web: gli utenti lo scaricano dalla pagina /app-desktop. L'APK è facoltativo in desktop.json, così una build Android fallita non toglie il download di Windows.
- Identità: Keycloak 26 (OIDC), realm versionato in infra/keycloak/realm; utenti demo solo in infra/keycloak/dev. Frontend con oidc-client-ts (Authorization Code + PKCE).
- Infrastruttura locale: infra/docker/compose.yml (compatibile con Docker e Podman).
- Infrastruttura cloud: un server Linux (VPS) creato dal pannello del fornitore e configurato dal workflow Prepara server. Terraform per Oracle Cloud è stato rimosso (ADR 0007, superata).
- AI: modulo `ai/` in apps/api, provider-agnostico. Assistente chat nel frontend in `features/assistant`.
- Multi-tenant: ogni tenant è un'azienda cliente oppure un tenant demo. Un'unica istanza serve più tenant, con Row Level Security su PostgreSQL.

## Ambiente di lavoro

- Il progetto gira in Claude Code sul web, con GitHub collegato. `gh` è preconfigurato per la rete della sessione: preferiscilo a `curl` per le operazioni GitHub.
- Il repository del progetto è `<owner>/gestionale`, privato. Se il repository non esiste ancora, crealo solo quando l'utente lo chiede esplicitamente nel task.
- Le modifiche si fanno su branch dedicati (`feat/…`, `fix/…`, `chore/…`), non direttamente su `main`.
- Per integrare il lavoro apri una pull request con `gh pr create`: titolo in stile Conventional Commits, descrizione con cosa cambia, perché, e come è stato testato.
- La CI deve passare prima di proporre la merge. Nessuna installazione in produzione senza approvazione umana: `release-images.yml` (solo da `main`, amd64 e arm64) pubblica le immagini e indica la versione pronta; Deploy si avvia a mano, e l'avvio è l'approvazione (repository privato: i Required reviewers degli environment richiedono GitHub Enterprise). La variabile `AUTO_DEPLOY` (deploy automatico in attesa di approvazione) si attiva solo se l'environment `production` ha i Required reviewers. Produzione su un server Linux via SSH (VPS come Aruba o Oracle Cloud; file in `infra/deploy/oracle`, guide `docs/deploy-vps.md` e `docs/deploy-oracle-cloud.md`, ADR 0002 e 0009). Il server si prepara con il workflow Prepara server (`infra/deploy/oracle/prepare-server.sh`, idempotente).
- Ogni modifica che richiede nuove variabili di produzione aggiorna `infra/deploy/oracle/.env.example`, `infra/deploy/oracle/compose.yml` e `docs/deploy-oracle-cloud.md`. I segreti di produzione stanno solo nel `.env` della macchina e negli environment GitHub.

## Regole
- Nessun segreto nel repository. Le credenziali arrivano da variabili d'ambiente o da GitHub Secrets.
- Ogni modifica al database passa da una migrazione Flyway nuova. Non modificare migrazioni già applicate.
- Ogni tabella di dominio ha `tenant_id NOT NULL` e la Row Level Security attiva. Nessuna query bypassa il tenant corrente, e non si usano connessioni con ruolo superuser dall'applicazione.
- Ogni endpoint filtra i dati per tenant e per utente autenticato. Nessun endpoint restituisce dati di altri tenant o di altri utenti senza autorizzazione esplicita.
- Le funzionalità si controllano sempre nel backend con `EntitlementService` (piano del tenant) e con i permessi del ruolo. Il frontend nasconde la UI ma non è una protezione.
- Profilazione: in Keycloak si assegnano solo ruoli; la mappatura ruolo → permessi sta in `Role`/`Permission` (apps/api, modulo `auth/`). Ogni endpoint dichiara il permesso con `@PreAuthorize("hasAuthority('…')")`. Un nuovo ruolo si aggiunge nel realm, in `Role`, nelle etichette del frontend e nella documentazione.
- Il codice dipende solo da claim OIDC neutri (`sub`, `tenant_id`, `roles`, `aud`), mai da API specifiche di Keycloak: il prodotto viene rivenduto e il provider di identità deve restare sostituibile. Unica eccezione: la gestione utenti della console di piattaforma, isolata dietro `IdentityAdmin` (implementazione `KeycloakIdentityAdmin`, ADR 0010).
- Console di piattaforma (`/console`, `/api/platform/**`, ADR 0010): solo per gli amministratori della piattaforma (realm `piattaforma`, ruolo `platform-admin`, OTP obbligatoria). Mai dati di dominio delle aziende nella console; accesso cross-tenant solo con `WorkerContext` `platform-admin` sulle tabelle con la policy dedicata (`tenants`, `licenses`, `platform_audit`). Ogni modifica scrive in `platform_audit` (mai password). Nessun token dei dipendenti verso `/api/platform`.
- La configurazione di un'installazione (URL, client, chiavi pubbliche) arriva da variabili d'ambiente o da `config.json`, mai ricompilando: la stessa build serve clienti diversi.
- Il codice web resta unico per browser e desktop: le differenze passano da `isPackagedDesktopApp()` e dalla configurazione, mai da build separate. Firma del codice e chiavi degli aggiornamenti stanno solo nei GitHub Secrets.
- Le modifiche al realm si fanno nel file JSON versionato; non committare mai utenti reali, password reali o segreti di client.
- Le feature flag tecniche e gli entitlement commerciali sono concetti distinti: non usare un flag per decidere cosa include un piano.
- Le licenze offline sono token firmati. La chiave privata non sta mai nel repository né nell'app distribuita.
- Assistente AI: i tool esposti al modello sono wrapper sottili sui servizi del dominio, eseguiti con i permessi dell'utente. Le operazioni di scrittura richiedono conferma esplicita dell'utente prima dell'esecuzione. Ogni chiamata registra tenant, utente, modello, token e costo stimato.
- Dati personali verso provider AI: inviare solo ciò che serve al task, e solo se il tenant ha un contratto di elaborazione dati e la configurazione lo consente.
- Privacy: solo cookie e archiviazioni tecniche (niente statistiche, font o contenuti esterni senza un banner di consenso deciso prima). Ogni nuovo cookie o archivio nel browser va nella pagina `/privacy` e in `docs/privacy-gdpr.md`. I dati offline appartengono a un solo utente (`LocalDataService`) e si cancellano all'uscita (`LogoutService`): una nuova tabella Dexie va inclusa in `LocalDataService.clear()`.
- Notifiche: il codice di dominio le crea solo con `NotificationService.send(...)` (mai insert diretti); i destinatari si risolvono dalla rubrica `app_users`. La presa visione è distinta dalla lettura e si registra con l'ora del server.
- Processi di sistema che lavorano per tutti i tenant: mai disattivare la RLS. Si usa `WorkerContext` (app.worker) con una policy dedicata solo sulla tabella tecnica che serve, documentata in un ADR.
- Interfaccia: solo componenti Angular Material. Colori, forme e tipografia solo dalle variabili di sistema (`var(--mat-sys-…)`), mai colori fissi. L'accento giallo (`--mat-sys-tertiary`, con testo `--mat-sys-on-tertiary`) si usa solo come riempimento, mai come colore del testo: il marchio del cliente (`branding` in config.json) deve applicarsi ovunque. Font e icone restano inclusi nell'app (niente Google Fonts o CDN esterni).
- Ogni nuova libreria UI o di componenti va verificata sulla licenza prima di adottarla: il prodotto viene rivenduto (es. PrimeNG è commerciale oltre certe soglie, vedi ADR 0006).
- Gli ID delle entità sono UUID generati dal client (preferibilmente v7).
- Le scritture offline passano dalla tabella outbox. Non chiamare direttamente le API di scrittura dai componenti. Eccezione: la console di piattaforma, solo online (`ConsoleApiService`).
- Prima di concludere un task: build e test del modulo toccato devono passare.
- Aggiorna sempre la documentazione nello stesso branch del codice: README (avvio e comandi), docs/architettura.md, un ADR in docs/decisioni/ per le scelte nuove, i README dei moduli in infra/ e apps/.
- Commit con Conventional Commits in italiano o inglese, coerente con lo storico.
- Creare repository e fare `push` è consentito solo se il task lo chiede. Non fare push diretti su `main`: usa un branch e una pull request.
- Non stampare, copiare o committare token GitHub. I segreti per il deploy si impostano dall'interfaccia GitHub o con `gh secret set` da parte dell'utente, mai scritti nel codice.
- Non committare `.env` né file con credenziali. Se li trovi tracciati, fermati e segnalalo.
