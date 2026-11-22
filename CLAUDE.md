# CLAUDE.md — gestionale

## Stack
- Backend: Spring Boot (Java 21, Maven) in apps/api, PostgreSQL, Flyway per le migrazioni.
- Frontend: Angular con Ionic e Capacitor (mobile), PWA per il web, Dexie per il database locale in apps/web.
- Desktop: Tauri 2 in apps/desktop, che incapsula la build web.
- Infrastruttura locale: infra/docker/compose.yml.
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
- Le feature flag tecniche e gli entitlement commerciali sono concetti distinti: non usare un flag per decidere cosa include un piano.
- Le licenze offline sono token firmati. La chiave privata non sta mai nel repository né nell'app distribuita.
- Assistente AI: i tool esposti al modello sono wrapper sottili sui servizi del dominio, eseguiti con i permessi dell'utente. Le operazioni di scrittura richiedono conferma esplicita dell'utente prima dell'esecuzione. Ogni chiamata registra tenant, utente, modello, token e costo stimato.
- Dati personali verso provider AI: inviare solo ciò che serve al task, e solo se il tenant ha un contratto di elaborazione dati e la configurazione lo consente.
- Gli ID delle entità sono UUID generati dal client (preferibilmente v7).
- Le scritture offline passano dalla tabella outbox. Non chiamare direttamente le API di scrittura dai componenti.
- Prima di concludere un task: build e test del modulo toccato devono passare.
- Commit con Conventional Commits in italiano o inglese, coerente con lo storico.
- Creare repository e fare `push` è consentito solo se il task lo chiede. Non fare push diretti su `main`: usa un branch e una pull request.
- Non stampare, copiare o committare token GitHub. I segreti per il deploy si impostano dall'interfaccia GitHub o con `gh secret set` da parte dell'utente, mai scritti nel codice.
- Non committare `.env` né file con credenziali. Se li trovi tracciati, fermati e segnalalo.
