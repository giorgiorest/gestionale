# CLAUDE.md — gestionale (prodotto: Civil Beefy)

Il nome del prodotto mostrato agli utenti è «Civil Beefy» (iniziali maiuscole). `gestionale` è
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
- AI: modulo `ia/` in apps/api, provider-agnostico. Assistente chat nel frontend in `features/assistant`.
- Multi-tenant: ogni tenant è un'azienda cliente oppure un tenant demo. Un'unica istanza serve più tenant, con Row Level Security su PostgreSQL.

## Ambiente di lavoro

- Il progetto gira in Claude Code sul web, con GitHub collegato. `gh` è preconfigurato per la rete della sessione: preferiscilo a `curl` per le operazioni GitHub.
- A ogni avvio di sessione l'hook `SessionStart` (`.claude/hooks/session-start.sh`) stampa branch,
  pull request aperta, prima voce di roadmap non completata e primo numero Flyway libero: parti da
  quelle righe invece di ricostruire lo stato con `git log` e letture di file.
- Il repository del progetto è `<owner>/gestionale`, privato. Se il repository non esiste ancora, crealo solo quando l'utente lo chiede esplicitamente nel task.
- Le modifiche si fanno su branch dedicati (`feat/…`, `fix/…`, `chore/…`), non direttamente su `main`.
- Per integrare il lavoro apri una pull request con `gh pr create`: titolo in stile Conventional Commits, descrizione con cosa cambia, perché, e come è stato testato.
- La CI deve passare prima di proporre la merge. Nessuna installazione in produzione senza approvazione umana: `release-images.yml` (solo da `main`, amd64 e arm64) pubblica le immagini, poi il job `approvazione` apre un'issue e aspetta `/deploy` da chi ha accesso in scrittura (`.github/scripts/attendi-approvazione.sh`, al massimo 30 minuti) prima del job `deploy`; Deploy si può sempre avviare a mano (repository privato: i Required reviewers degli environment richiedono GitHub Enterprise). La variabile `AUTO_DEPLOY` (deploy automatico in attesa di approvazione) si attiva solo se l'environment `production` ha i Required reviewers. Produzione su un server Linux via SSH (VPS come Aruba o Oracle Cloud; file in `infra/deploy/oracle`, guide `docs/deploy-vps.md` e `docs/deploy-oracle-cloud.md`, ADR 0002 e 0009). Il server si prepara con il workflow Prepara server (`infra/deploy/oracle/prepare-server.sh`, idempotente).
- Ogni modifica che richiede nuove variabili di produzione aggiorna `infra/deploy/oracle/.env.example`, `infra/deploy/oracle/compose.yml` e `docs/deploy-oracle-cloud.md`. I segreti di produzione stanno solo nel `.env` della macchina e negli environment GitHub.

## Prodotto e lavoro da fare

Verticale: **imprese edili e di cantiere** (ADR 0012). Il lavoro da fare, diviso in pull request, è
in `docs/roadmap.md`: è la fonte di verità, va aggiornata nella stessa PR che completa una voce.

## Mappa del codice

| Dove | Cosa c'è |
|------|----------|
| `apps/api/src/main/java/it/gestionale/api/` | Monolite modulare, un pacchetto per modulo, in italiano (ADR 0023). Dall'alto in basso: orchestrazione `sincronizzazione/`, `piattaforma/` (console), `demo/`, `errori/` (gestore errori API), `configurazione/`, `salute/`; dominio `clienti/` (il modello da copiare), `fornitori/`, `risorse/`, `commesse/`, `assegnazioni/`, `lavorazioni/`, `rapportini/` (rapportino, righe ore, materiali), `riepilogoore/`, `costimanodopera/`; trasversali `notifiche/`, `allegati/`, `ia/`, `osservabilita/`; sicurezza `accesso/` (ruoli→permessi), `licenze/` (piani, licenze offline); fondamenta `tenant/` (tenant dal JWT, RLS), `condiviso/` (`PaginaDto`) |
| `apps/api/src/main/resources/db/migration/` | migrazioni Flyway `V1`…`V5` |
| `apps/api/src/test/java/` | test di integrazione con Testcontainers; `TestIntegrazioneBase`, `DatiDiTest`, `JwtDiTest` sono la base da riusare |
| `apps/web/src/app/core/` | `auth/`, `profile/` (permessi), `entitlements/` (piano), `sync/` (outbox), `notifications/`, `privacy/`, `branding/`, `config/`, `layout/` |
| `apps/web/src/app/features/` | una cartella per pagina; `features/customers/` è il modello da copiare |
| `apps/web/src/app/db/local-db.ts` | schema Dexie (IndexedDB) |
| `infra/docker/` | compose per lo sviluppo, Dockerfile web, nginx |
| `infra/deploy/oracle/` | compose di produzione, `.env.example`, `prepare-server.sh`, Caddyfile |
| `docs/decisioni/` | ADR: leggi quello pertinente prima di cambiare una scelta architetturale |

## Ricettario

### Nuova entità di dominio

1. **Migrazione** `V<n>__nome.sql` (numero: vedi ADR 0013):
   ```sql
   CREATE TABLE commesse (
       id         UUID PRIMARY KEY,                               -- UUID v7 dal client
       tenant_id  UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
       ...
       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
       updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX commesse_tenant_id_idx ON commesse (tenant_id);
   CALL add_sync_columns('commesse');   -- deleted_at, version, created_by, updated_by (ADR 0018)
   CALL enable_tenant_rls('commesse');
   ```
   `add_sync_columns` ci va **subito**: aggiungere quelle colonne dopo, su una tabella piena, costa
   di più. L'indice dell'elenco e i vincoli di unicità applicativi (es. la P.IVA) sono **parziali**,
   `WHERE deleted_at IS NULL`: altrimenti l'elenco riordina anche i tombstone e un record cancellato
   impedisce di reinserire lo stesso dato.
2. **Backend**: copia la struttura di `clienti/` → `Entity`, `Repository`, `Dto` (con `Input`
   validato), `Service` (`@Transactional`, filtra per `ContestoTenant.require()` anche se la RLS già
   protegge: difesa in profondità), `Controller` (`@PreAuthorize("hasAuthority('…')")`, upsert
   idempotente con `PUT /{id}`, cancellazione logica idempotente con `DELETE /{id}` → `204`).
   Le letture filtrano `deleted_at IS NULL` (portalo nel nome del metodo del repository, come
   `findByTenantIdAndDeletedAtIsNullOrderByNameAsc`); l'utente arriva al servizio come
   `jwt.getSubject()` passato dal controller, per `created_by`/`updated_by`.
3. **Permesso nuovo**: `accesso/Permesso.java` + realm in `infra/keycloak/realm` + etichette nel
   frontend + `docs/gestione-utenti-e-aziende.md`.
4. **Funzionalità di piano nuova**: `licenze/Funzionalita.java` + console di piattaforma.
5. **Frontend**: copia `features/customers/` (service + page), rotta in `app.routes.ts` con
   `permissionGuard` ed eventuale `featureGuard`.
6. **Offline**: tabella in `db/local-db.ts` con una **nuova** `version()` (mai modificare quelle
   esistenti), e aggiungila a `LocalDataService.clear()` (regola di privacy, ADR 0008).
7. **Scritture dal client**: solo `SyncService.enqueue()`, mai chiamate dirette alle API di
   scrittura (eccezione: la console di piattaforma, `ConsoleApiService`). La cancellazione toglie il
   record dalla copia locale e accoda `delete`; il caricamento successivo chiede a
   `SyncService.pendingByEntity()` cosa è ancora in coda, per non rimettere in locale un record già
   cancellato né buttarne uno non ancora creato (vedi `CustomersService.load()`).
8. **Test**: un test di integrazione che estende `TestIntegrazioneBase`, incluso il caso
   «un tenant non vede i dati di un altro» (vedi `IsolamentoTenantTest`) e il `DELETE` ripetuto
   (idempotenza, vedi `ClienteControllerTest`).
9. **Documentazione**: tabella dei moduli nel README, `docs/architettura.md`, e un ADR se la scelta
   è nuova.

### Liste (paginazione, ordinamento, ricerca)

Convenzione unica in ADR 0014: `?page=&size=&sort=&q=`, risposta `PaginaDto<T>` esplicita, indice su
`(tenant_id, campo di ordinamento)`. La paginazione **non** è il meccanismo di sincronizzazione.

### Comandi

```bash
cd apps/api && ./mvnw -q test -Dtest=NomeDelTest   # un solo test, non tutta la suite
cd apps/api && ./mvnw -q -DskipTests package
cd apps/web && npm test -- --watch=false
cd apps/web && npm run build -- --configuration production
```

## Lavorare in modo efficiente in questo repository

- **Spesa minima, resa massima**: a parità di affidabilità, scegli sempre l'opzione più economica
  (in token, tempo, chiamate). Non duplicare lavoro già fatto in sessione, non rileggere file già
  letti, non rilanciare una build o un test già passati senza un motivo concreto per dubitarne.
- **Una pull request = una voce di `docs/roadmap.md` = una sessione.** Se non ci sta, va spezzata
  nella roadmap prima di iniziare.
- **Non cercare dove sta una cosa**: usa la mappa qui sopra e i percorsi indicati nel prompt.
- **Test mirati**, non la suite intera: i test di integrazione avviano PostgreSQL con Testcontainers
  e costano minuti. La suite completa la esegue la CI.
- **Output lungo su file**, non a schermo: `… > /tmp/out.log 2>&1; tail -30 /tmp/out.log`.
  Vale per build, test e log dei container.
- **Prima il piano** sulle voci grandi, poi il codice.
- **Chiusura di ogni PR**: `/code-review` + `/simplify`, ma il livello con sub-agenti (4 per
  `/simplify`, 8 angoli per `/code-review` `high`) è il **default più costoso, non quello
  predefinito**. Fai tu stesso, in un solo passaggio e senza sub-agenti, la revisione di:
  aggiungere permessi a ruoli già esistenti, copiare la struttura di un'entità già costruita
  (`clienti/`, `commesse/`, …), un fix a un fix. Il livello pieno con sub-agenti serve solo
  quando la PR **introduce ex novo** RLS su una tabella nuova, allegati, fatturazione o pagamenti
  — non quando li estende o li collega a qualcosa già esistente. `/security-review` sulle voci
  marcate 🔐 nella roadmap.
- **Una sessione, una storia**: non incatenare più storie nella stessa sessione — ogni storia
  nuova in una sessione già lunga eredita tutta la cronologia precedente, anche quando non serve
  più. Chiudi la sessione a PR aperta (o mersa) e aprine una nuova per la prossima voce.
- **Log lunghi**: mai l'output intero in contesto. Scarica su file e usa `grep` per l'errore
  specifico (es. `Caused by:`, `\[ERROR\]`); per un log CI già troppo grande per un tool, salvalo
  su file e filtralo con `grep`/`sed`, non rileggerlo per intero.
- Dopo aver aperto la PR, **iscriviti alla sua attività** per correggere i fallimenti della CI
  quando arrivano, senza attendere né ricontrollare a vuoto.

## Regole
- Nessun segreto nel repository. Le credenziali arrivano da variabili d'ambiente o da GitHub Secrets.
- Ogni modifica al database passa da una migrazione Flyway nuova. Non modificare migrazioni già applicate.
- Ogni tabella di dominio ha `tenant_id NOT NULL` e la Row Level Security attiva. Nessuna query bypassa il tenant corrente, e non si usano connessioni con ruolo superuser dall'applicazione.
- Moduli del backend (ADR 0023): package e classi nuovi sono **in italiano**; restano in inglese solo i suffissi di ruolo tecnico (`Controller`, `Service`, `Repository`, `Dto`, `Config`, `Properties`, `Converter`, `Filter`, `Exception`, `Test`), i nomi di framework e il nome tecnico `gestionale`. Le dipendenze tra moduli vanno solo verso il basso (orchestrazione → dominio → trasversali → sicurezza → fondamenta) e senza cicli: lo verifica `ArchitetturaModulareTest`. Un modulo che deve reagire a un altro dello stesso livello o superiore pubblica un evento (esempio: `UtenteRegistrato`), non lo chiama. URL REST, tabelle, colonne, campi JSON e nomi dei permessi restano in inglese: sono il contratto con i client installati.
- Ogni endpoint filtra i dati per tenant e per utente autenticato. Nessun endpoint restituisce dati di altri tenant o di altri utenti senza autorizzazione esplicita.
- Le funzionalità si controllano sempre nel backend con `AbilitazioniService` (piano del tenant) e con i permessi del ruolo. Il frontend nasconde la UI ma non è una protezione.
- Profilazione: in Keycloak si assegnano solo ruoli; la mappatura ruolo → permessi sta in `Ruolo`/`Permesso` (apps/api, modulo `accesso/`). Ogni endpoint dichiara il permesso con `@PreAuthorize("hasAuthority('…')")`. Un nuovo ruolo si aggiunge nel realm, in `Ruolo`, nelle etichette del frontend e nella documentazione.
- Il codice dipende solo da claim OIDC neutri (`sub`, `tenant_id`, `roles`, `aud`), mai da API specifiche di Keycloak: il prodotto viene rivenduto e il provider di identità deve restare sostituibile. Unica eccezione: la gestione utenti della console di piattaforma, isolata dietro `AmministrazioneIdentita` (implementazione `KeycloakAmministrazioneIdentita`, ADR 0010).
- Console di piattaforma (`/console`, `/api/platform/**`, ADR 0010): solo per gli amministratori della piattaforma (realm `piattaforma`, ruolo `platform-admin`, OTP obbligatoria). Mai dati di dominio delle aziende nella console; accesso cross-tenant solo con `ContestoWorker` `platform-admin` sulle tabelle con la policy dedicata (`tenants`, `licenses`, `platform_audit`). Ogni modifica scrive in `platform_audit` (mai password). Nessun token dei dipendenti verso `/api/platform`.
- La configurazione di un'installazione (URL, client, chiavi pubbliche) arriva da variabili d'ambiente o da `config.json`, mai ricompilando: la stessa build serve clienti diversi.
- Il codice web resta unico per browser e desktop: le differenze passano da `isPackagedDesktopApp()` e dalla configurazione, mai da build separate. Firma del codice e chiavi degli aggiornamenti stanno solo nei GitHub Secrets.
- Le modifiche al realm si fanno nel file JSON versionato; non committare mai utenti reali, password reali o segreti di client.
- Le feature flag tecniche e gli entitlement commerciali sono concetti distinti: non usare un flag per decidere cosa include un piano.
- Le licenze offline sono token firmati. La chiave privata non sta mai nel repository né nell'app distribuita.
- Assistente AI: i tool esposti al modello sono wrapper sottili sui servizi del dominio, eseguiti con i permessi dell'utente. Le operazioni di scrittura richiedono conferma esplicita dell'utente prima dell'esecuzione. Ogni chiamata registra tenant, utente, modello, token e costo stimato.
- Dati personali verso provider AI: inviare solo ciò che serve al task, e solo se il tenant ha un contratto di elaborazione dati e la configurazione lo consente.
- Privacy: solo cookie e archiviazioni tecniche (niente statistiche, font o contenuti esterni senza un banner di consenso deciso prima). Ogni nuovo cookie o archivio nel browser va nella pagina `/privacy` e in `docs/privacy-gdpr.md`. I dati offline appartengono a un solo utente (`LocalDataService`) e si cancellano all'uscita (`LogoutService`): una nuova tabella Dexie va inclusa in `LocalDataService.clear()`.
- Notifiche: il codice di dominio le crea solo con `NotificaService.send(...)` (mai insert diretti); i destinatari si risolvono dalla rubrica `app_users`. La presa visione è distinta dalla lettura e si registra con l'ora del server.
- Processi di sistema che lavorano per tutti i tenant: mai disattivare la RLS. Si usa `ContestoWorker` (app.worker) con una policy dedicata solo sulla tabella tecnica che serve, documentata in un ADR.
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
