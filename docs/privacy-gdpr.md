# Privacy, cookie e GDPR

Cosa fa Civil Beefy per la protezione dei dati, cosa resta a carico di chi vende o installa il
servizio, e cosa manca ancora. **Non è una consulenza legale**: prima di vendere il servizio fai
verificare contratti e informative da un legale o da un DPO.

## 1. Chi è chi

| Ruolo GDPR | Chi | Cosa comporta |
|------------|-----|---------------|
| **Titolare del trattamento** (art. 24) | l'**azienda cliente**, per i dati dei suoi dipendenti e dei suoi clienti | decide perché e come trattare i dati; dà l'informativa ai dipendenti |
| **Responsabile del trattamento** (art. 28) | **chi vende il servizio** (SaaS) e lo gestisce in cloud | tratta i dati solo per conto del cliente, con un contratto (DPA) |
| **Sub-responsabili** | i fornitori usati dal servizio (tabella sotto) | vanno elencati nel DPA e autorizzati dal cliente |
| **Titolare autonomo** | chi vende, per i dati dei referenti commerciali e della fatturazione | informativa propria sul sito commerciale |

Se il cliente installa Civil Beefy sui **propri** server e lo gestisce da sé, chi vende non tratta
dati personali: fornisce solo il software (resta l'eventuale assistenza con accesso ai dati, che
richiede comunque un DPA).

### Sub-responsabili tipici di un'installazione SaaS

| Fornitore | Servizio | Dati | Dove |
|-----------|----------|------|------|
| Oracle Cloud (o altro hosting) | macchina, dischi, backup | tutti i dati dell'applicazione | regione scelta (es. Torino) |
| Servizio SMTP (es. Brevo, OCI Email Delivery) | invio email delle notifiche | email, nome, testo della notifica | da verificare nel DPA del fornitore |
| Provider AI (solo se attivo l'assistente) | risposte dell'assistente | solo i dati necessari alla richiesta, se il tenant ha un DPA e la configurazione lo consente | da verificare |
| GitHub | codice e pipeline | **nessun dato personale dei clienti** | — |

## 2. Cookie e dati sul dispositivo

Civil Beefy usa **solo cookie tecnici**: secondo le Linee guida del Garante (provvedimento del
10 giugno 2021) **non serve il banner di consenso**, serve solo l'informazione. È nella pagina pubblica
**`/privacy`** («Privacy e cookie»), raggiungibile dalla pagina di accesso e dal menu utente.

| Nome | Tipo | Chi lo imposta | Scopo | Durata |
|------|------|----------------|-------|--------|
| `AUTH_SESSION_ID`, `KC_RESTART` | cookie tecnico | Keycloak | procedura di accesso in corso | durante l'accesso |
| `KEYCLOAK_IDENTITY`, `KEYCLOAK_SESSION` | cookie tecnico | Keycloak | sessione di accesso (SSO) | fino all'uscita, al massimo 10 ore |
| `KEYCLOAK_REMEMBER_ME` | cookie tecnico | Keycloak | ricorda il nome utente, solo se l'utente sceglie «Ricordami» | persistente |
| `KEYCLOAK_LOCALE` | cookie tecnico | Keycloak | lingua della pagina di accesso | sessione |
| `oidc.*` | sessionStorage | web app | token di accesso | chiusura della scheda o uscita |
| `oidc.*` (stato del login) | localStorage | web app | dati temporanei della procedura di accesso | rimossi a fine accesso |
| database `gestionale` | IndexedDB | web app | copia offline di clienti e notifiche, modifiche in attesa | **cancellato all'uscita** o se accede un altro utente |
| cache del service worker | Cache Storage | web app | file dell'applicazione, senza dati personali | fino all'aggiornamento |
| `gestionale.serverUrl`, `gestionale.config` | localStorage | app desktop | server dell'azienda e sua configurazione | fino a «Cambia server» |

Nessun cookie di profilazione, nessuno strumento di statistica, nessun contenuto di terze parti:
font (Inter) e icone (Material Symbols) sono inclusi nell'app. Eccezione possibile: un **logo del
cliente su un altro dominio** (`BRAND_LOGO_URL`) fa arrivare a quel dominio l'IP di chi apre l'app:
meglio ospitarlo sullo stesso sito.

**Regola per gli sviluppi futuri**: un nuovo cookie o archivio nel browser va aggiunto alla pagina
`/privacy` (`apps/web/src/app/features/privacy`) e a questa tabella. Strumenti di statistica, mappe,
video o font esterni richiederebbero il **banner di consenso**: vanno evitati o discussi prima.

### Dati offline legati all'utente

Il database locale contiene dati personali. Per non lasciarli a chi usa lo stesso PC dopo:

- registra a quale utente appartiene (`tenant_id:sub`); se sullo stesso browser accede un altro
  utente, viene **svuotato prima** di mostrare qualsiasi pagina;
- all'**uscita** viene sempre svuotato; se ci sono modifiche non ancora inviate (offline) l'app le
  invia o chiede conferma prima di cancellarle;
- nell'app desktop «Cambia server» cancella anche i dati locali.

## 3. Misure tecniche già presenti (art. 25 e 32)

| Misura | Dove |
|--------|------|
| Separazione dei dati tra aziende con **Row Level Security** su PostgreSQL; l'app usa un utente non superuser | `apps/api`, migrazioni Flyway, ADR 0001 |
| Ogni endpoint verifica **ruolo** e **piano**; i dati sono filtrati per azienda e utente | `@PreAuthorize`, `EntitlementService` |
| **HTTPS** obbligatorio con HSTS; solo le porte 80/443 esposte | Caddy, `infra/deploy/oracle` |
| Login con **Authorization Code + PKCE**, token in sessionStorage | `oidc-client-ts`, Keycloak |
| **Password** di almeno 10 caratteri, diverse da nome utente ed email; **blocco** dopo 10 tentativi falliti | realm `gestionale` |
| Sessione: scade dopo **30 minuti** di inattività, al massimo **10 ore** | realm `gestionale` |
| **Registro degli accessi** (login riusciti e falliti, IP) conservato **90 giorni**; registro delle operazioni degli amministratori | realm `gestionale` (eventi) |
| Amministrazione della piattaforma con account separati, **OTP obbligatoria**, nessun accesso ai dati di dominio delle aziende, **registro delle operazioni** (`platform_audit`) | console di piattaforma, ADR 0010 |
| Sospensione immediata di un'azienda; disattivazione di un utente con chiusura delle sessioni | console di piattaforma |
| Dischi della macchina **cifrati** dal fornitore cloud (Oracle: sempre) | infrastruttura |
| Nessun segreto nel codice; segreti di produzione solo sulla macchina e nei GitHub Secrets | `CLAUDE.md` |
| Dati personali verso l'AI solo se necessari e con DPA del tenant | modulo `ai/` |

Il realm si importa solo al **primo avvio** di Keycloak. In un'installazione già esistente le nuove
impostazioni (es. eventi) vanno attivate anche dalla console: *Realm settings → Events*.

## 4. Cosa deve fare chi vende il servizio (fuori dal software)

1. **Contratto di responsabile del trattamento (DPA, art. 28)** con ogni cliente: oggetto, durata,
   tipi di dati, misure di sicurezza (sezione 3), elenco dei sub-responsabili, assistenza per i
   diritti degli interessati, restituzione o cancellazione dei dati a fine contratto, audit.
2. **DPA con i propri fornitori** (hosting, SMTP, AI): accettare quelli standard dei fornitori e
   verificare dove trattano i dati (preferibilmente UE).
3. **Registro dei trattamenti come responsabile** (art. 30, par. 2).
4. **Procedura per le violazioni di dati** (data breach): avvisare il cliente senza ritardo (il
   cliente ha 72 ore per notificare il Garante). Tieni un registro delle violazioni.
5. **Informativa privacy** del sito commerciale e **condizioni d'uso** del servizio: configurabili
   nell'app con `LEGAL_PRIVACY_URL` e `LEGAL_TERMS_URL`, mostrate nella pagina `/privacy`.
6. **Modello di informativa per i dipendenti** del cliente (la dà il cliente come titolare): utile
   fornirlo insieme al contratto, con la descrizione dei dati trattati dal gestionale.
7. **Backup**: cifrati, in UE, con tempi di conservazione scritti nel DPA (es. 14 giorni, come nella
   guida di deploy). Prova periodicamente il ripristino.
8. **Accessi alla produzione**: solo persone autorizzate, chiavi SSH personali, amministratori
   Keycloak nominativi; valuta l'autenticazione a due fattori per gli amministratori.

## 5. Diritti degli interessati: come si fa oggi

| Richiesta | Come |
|-----------|------|
| **Accesso / copia dei dati** di un dipendente | profilo in Keycloak (console) e, nel database, `app_users`, le notifiche ricevute e i record che ha creato o modificato (colonne `created_by`/`updated_by` delle tabelle di dominio) |
| **Rettifica** | nome ed email in Keycloak (l'app li aggiorna al successivo accesso) |
| **Cancellazione** di un dipendente | in Keycloak: disattivare o eliminare l'utente; nel database: `DELETE FROM email_deliveries WHERE tenant_id = '…' AND user_id = '…'` e `DELETE FROM app_users WHERE tenant_id = '…' AND user_id = '…'` (cancella anche le sue ricevute di lettura); poi azzerare le tracce di chi ha scritto, una tabella di dominio per volta: `UPDATE customers SET created_by = NULL WHERE created_by = '…'` e lo stesso per `updated_by`. `user_id` è l'ID dell'utente in Keycloak |
| **Fine contratto di un cliente** | `DELETE FROM tenants WHERE id = '…'`: cancella a cascata tutti i dati dell'azienda; poi eliminare i suoi utenti in Keycloak. I backup scadono secondo la conservazione |

Le operazioni sul database si fanno sulla macchina, con l'utente amministratore del database
(vedi [gestione-utenti-e-aziende.md](gestione-utenti-e-aziende.md)).

## 6. Cosa manca ancora (da pianificare)

- **Esportazione e cancellazione dal software** (area amministrativa) invece che con SQL: dati di
  un dipendente, chiusura di un'azienda con esportazione finale.
- **Conservazione** automatica: cancellazione delle notifiche e della coda email più vecchie di un
  periodo definito nel DPA.
- **Registro delle operazioni** nell'applicazione (chi ha modificato cosa), oltre agli eventi di Keycloak:
  oggi le tabelle di dominio dicono solo chi ha scritto per **ultimo** (`created_by`/`updated_by`,
  [ADR 0018](decisioni/0018-cancellazione-logica.md)), non lo storico.
- **Conservazione dei record cancellati**: la cancellazione dall'applicazione è logica (`deleted_at`),
  quindi la riga resta per poter propagare la cancellazione ai dispositivi offline. Serve una regola che
  stabilisca dopo quanto tempo il record viene cancellato per davvero; la cancellazione su richiesta
  dell'interessato (art. 17) resta una cancellazione fisica, da fare con SQL come sopra.
- **Autenticazione a due fattori** obbligatoria per il ruolo `admin` (Keycloak la supporta già:
  *Authentication → Required actions → Configure OTP*).
- **Backup fuori dalla macchina** (oggi la guida li salva sulla stessa macchina).
- Esportazione dei dati di un'azienda dalla console (oggi: backup o SQL).
