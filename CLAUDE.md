# CLAUDE.md — gestionale

## Stack
- Backend: Spring Boot (Java 21, Maven) in apps/api, PostgreSQL, Flyway per le migrazioni.
- Frontend: Angular con Ionic e Capacitor (mobile), PWA per il web, Dexie per il database locale in apps/web.
- Desktop: Tauri 2 in apps/desktop, che incapsula la build web (Windows). Un solo installer per tutti i clienti: il server si sceglie al primo avvio. Installer generati da ci-desktop.yml.
- Identità: Keycloak 26 (OIDC), realm versionato in infra/keycloak/realm; utenti demo solo in infra/keycloak/dev. Frontend con oidc-client-ts (Authorization Code + PKCE).
- Infrastruttura locale: infra/docker/compose.yml (compatibile con Docker e Podman).
- AI: modulo `ai/` in apps/api, provider-agnostico. Assistente chat nel frontend in `features/assistant`.
- Multi-tenant: ogni tenant è un'azienda cliente oppure un tenant demo. Un'unica istanza serve più tenant, con Row Level Security su PostgreSQL.

## Ambiente di lavoro

- Il progetto gira in Claude Code sul web, con GitHub collegato. `gh` è preconfigurato per la rete della sessione: preferiscilo a `curl` per le operazioni GitHub.
- Il repository del progetto è `<owner>/gestionale`, privato. Se il repository non esiste ancora, crealo solo quando l'utente lo chiede esplicitamente nel task.
- Le modifiche si fanno su branch dedicati (`feat/…`, `fix/…`, `chore/…`), non direttamente su `main`.
- Per integrare il lavoro apri una pull request con `gh pr create`: titolo in stile Conventional Commits, descrizione con cosa cambia, perché, e come è stato testato.
- La CI deve passare prima di proporre la merge. Il deploy non parte mai automaticamente: resta manuale finché non sarà definito il target.

## Regole
- Nessun segreto nel repository. Le credenziali arrivano da variabili d'ambiente o da GitHub Secrets.
- Ogni modifica al database passa da una migrazione Flyway nuova. Non modificare migrazioni già applicate.
- Ogni tabella di dominio ha `tenant_id NOT NULL` e la Row Level Security attiva. Nessuna query bypassa il tenant corrente, e non si usano connessioni con ruolo superuser dall'applicazione.
- Ogni endpoint filtra i dati per tenant e per utente autenticato. Nessun endpoint restituisce dati di altri tenant o di altri utenti senza autorizzazione esplicita.
- Le funzionalità si controllano sempre nel backend con `EntitlementService` (piano del tenant) e con i permessi del ruolo. Il frontend nasconde la UI ma non è una protezione.
- Profilazione: in Keycloak si assegnano solo ruoli; la mappatura ruolo → permessi sta in `Role`/`Permission` (apps/api, modulo `auth/`). Ogni endpoint dichiara il permesso con `@PreAuthorize("hasAuthority('…')")`. Un nuovo ruolo si aggiunge nel realm, in `Role`, nelle etichette del frontend e nella documentazione.
- Il codice dipende solo da claim OIDC neutri (`sub`, `tenant_id`, `roles`, `aud`), mai da API specifiche di Keycloak: il prodotto viene rivenduto e il provider di identità deve restare sostituibile.
- La configurazione di un'installazione (URL, client, chiavi pubbliche) arriva da variabili d'ambiente o da `config.json`, mai ricompilando: la stessa build serve clienti diversi.
- Il codice web resta unico per browser e desktop: le differenze passano da `isPackagedDesktopApp()` e dalla configurazione, mai da build separate. Firma del codice e chiavi degli aggiornamenti stanno solo nei GitHub Secrets.
- Le modifiche al realm si fanno nel file JSON versionato; non committare mai utenti reali, password reali o segreti di client.
- Le feature flag tecniche e gli entitlement commerciali sono concetti distinti: non usare un flag per decidere cosa include un piano.
- Le licenze offline sono token firmati. La chiave privata non sta mai nel repository né nell'app distribuita.
- Assistente AI: i tool esposti al modello sono wrapper sottili sui servizi del dominio, eseguiti con i permessi dell'utente. Le operazioni di scrittura richiedono conferma esplicita dell'utente prima dell'esecuzione. Ogni chiamata registra tenant, utente, modello, token e costo stimato.
- Dati personali verso provider AI: inviare solo ciò che serve al task, e solo se il tenant ha un contratto di elaborazione dati e la configurazione lo consente.
- Notifiche: il codice di dominio le crea solo con `NotificationService.send(...)` (mai insert diretti); i destinatari si risolvono dalla rubrica `app_users`. La presa visione è distinta dalla lettura e si registra con l'ora del server.
- Processi di sistema che lavorano per tutti i tenant: mai disattivare la RLS. Si usa `WorkerContext` (app.worker) con una policy dedicata solo sulla tabella tecnica che serve, documentata in un ADR.
- Nel codice caricato all'avvio (app.ts, app.config.ts, core/) i componenti Ionic si importano dagli entry point per componente (`@ionic/angular/ion-…`, `@ionic/angular/provide`), non dal barrel `@ionic/angular`, che porterebbe nel bundle iniziale tutti i componenti usati dalle pagine lazy.
- Gli ID delle entità sono UUID generati dal client (preferibilmente v7).
- Le scritture offline passano dalla tabella outbox. Non chiamare direttamente le API di scrittura dai componenti.
- Prima di concludere un task: build e test del modulo toccato devono passare.
- Aggiorna sempre la documentazione nello stesso branch del codice: README (avvio e comandi), docs/architettura.md, un ADR in docs/decisioni/ per le scelte nuove, i README dei moduli in infra/ e apps/.
- Commit con Conventional Commits in italiano o inglese, coerente con lo storico.
- Creare repository e fare `push` è consentito solo se il task lo chiede. Non fare push diretti su `main`: usa un branch e una pull request.
- Non stampare, copiare o committare token GitHub. I segreti per il deploy si impostano dall'interfaccia GitHub o con `gh secret set` da parte dell'utente, mai scritti nel codice.
- Non committare `.env` né file con credenziali. Se li trovi tracciati, fermati e segnalalo.
