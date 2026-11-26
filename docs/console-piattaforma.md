# Console di piattaforma

La console di piattaforma (`https://<dominio>/console`) è l'applicazione di amministrazione per
**chi gestisce il servizio**: crea le aziende clienti, ne gestisce licenze e utenti, sospende o
riattiva un'azienda, e tiene il registro di ogni operazione. **I dipendenti delle aziende non
possono entrarci**, nemmeno gli amministratori di un'azienda. Decisioni e motivi:
[ADR 0010](decisioni/0010-console-piattaforma.md).

## Come è protetta

| Livello | Protezione |
|---------|------------|
| Accesso | Realm Keycloak **separato** (`piattaforma`): i dipendenti non hanno un account lì |
| Login | Password di almeno 14 caratteri **e codice OTP obbligatorio** (app come Google Authenticator, Microsoft Authenticator); blocco dopo 5 tentativi falliti; sessione di 15 minuti di inattività, al massimo 8 ore |
| API | `/api/platform/**` accetta **solo** token del realm `piattaforma` con il ruolo `platform-admin`; un token di un dipendente viene rifiutato (401), anche se è amministratore della sua azienda |
| Database | Le aziende e le licenze di tutti si leggono con una policy dedicata (`app.worker = 'platform-admin'`); i dati delle aziende (clienti, notifiche…) restano **invisibili** alla console |
| Utenti | L'API gestisce gli utenti in Keycloak con un client di servizio che può solo gestire gli utenti del realm delle aziende (non i client, non gli altri realm) |
| Rete (facoltativo) | `ADMIN_ALLOWED_IPS` nel `.env`: console, login degli amministratori, console di Keycloak e osservabilità (`/actuator/*`, [ADR 0022](decisioni/0022-osservabilita.md)) rispondono solo da quegli indirizzi |
| Tracciabilità | Ogni operazione nel registro: chi, quando, su quale azienda, cosa (mai password) |

## Primo accesso (una volta, sul server di produzione)

Dopo il primo deploy con la console, crea il tuo account di amministratore della piattaforma:

1. Apri la console di Keycloak: `https://<dominio>/sso/admin`, utente `admin`, password sul server:
   `grep KEYCLOAK_ADMIN_PASSWORD /opt/civilbeefy/.env`.
2. In alto a sinistra scegli il realm **piattaforma** (non *gestionale*, non *master*).
3. **Users → Add user**: nome utente (es. il tuo nome), email, nome, cognome → **Create**.
4. **Role mapping → Assign role → Filter by realm roles** → `platform-admin` → **Assign**.
5. **Credentials → Set password**: una password provvisoria, *Temporary* attivo.
6. Apri `https://<dominio>/console`, entra con l'utente appena creato: Keycloak chiede di
   **scegliere una nuova password** (almeno 14 caratteri) e di **configurare l'OTP** inquadrando il
   QR code con l'app sul telefono.

Da qui in poi lavori solo nella console. Per un altro amministratore ripeti i passi 3–5.

L'utente `admin` di Keycloak (realm *master*) **non va eliminato**: lo usa il servizio
`keycloak-setup` per tenere allineata la configurazione. La sua password è casuale e sta solo nel
`.env`; per proteggerlo meglio imposta `ADMIN_ALLOWED_IPS`.

## Uso

**Nuova azienda** — *Aziende → Nuova azienda*: ragione sociale (la sigla si propone da sola),
piano, numero massimo di utenti, scadenza (vuota = senza scadenza), funzioni incluse
(es. assistente AI con la quota mensile).

**Nuovo utente** — nella pagina dell'azienda, *Utenti → Nuovo utente*: nome, cognome, email, nome
utente e ruolo (`Amministratore`, `Operatore`, `Consultazione`). La console mostra **una sola volta**
la password provvisoria: consegnala all'utente in modo riservato; al primo accesso ne sceglie una
sua. L'utente è già collegato all'azienda: niente `tenant_id` da copiare.

**Altre operazioni**

| Cosa | Dove |
|------|------|
| Cambiare ruolo | menu *Ruolo* nella riga dell'utente |
| Disattivare un utente (es. uscito dall'azienda) | interruttore *Attivo*: non entra più e le sue sessioni si chiudono subito |
| Password dimenticata | icona della chiave: nuova password provvisoria, sessioni chiuse |
| Cambiare piano, utenti massimi, scadenza, funzioni | scheda *Licenza* → *Salva licenza* |
| Sospendere un'azienda (es. mancato pagamento) | *Sospendi*: tutti i suoi utenti vengono bloccati entro 30 secondi; i dati restano |
| Riattivarla | *Riattiva* |
| Chi ha fatto cosa | *Registro delle operazioni* in fondo alla pagina dell'azienda |

Il limite di utenti della licenza è applicato: oltre il massimo la console rifiuta nuovi utenti.

## Installazioni esistenti

Al primo deploy con la console:

- **Prepara server** aggiunge al `.env` le voci nuove (`PLATFORM_KEYCLOAK_CLIENT_SECRET`,
  `ADMIN_ALLOWED_IPS`) senza toccare le altre: lancialo **prima** del Deploy (il Deploy si ferma e lo
  ricorda se manca il segreto).
- **Deploy** copia il nuovo realm `piattaforma`, riavvia Keycloak per importarlo e avvia
  `keycloak-setup`, che crea o aggiorna il client di servizio nel realm delle aziende.

Se `keycloak-setup` segnala che l'accesso amministrativo non riesce (utente `admin` eliminato o
password cambiata), crea il client a mano nel realm **gestionale**: *Clients → Create client* →
`gestionale-platform-admin`, *Client authentication* attivo, solo *Service accounts roles*;
*Credentials*: il segreto del `.env`; *Service accounts roles*: `realm-management` →
`view-users`, `query-users`, `manage-users`; *Client scopes*: aggiungi `kc-admin-api` come
*Default*; *Dedicated scope → Scope*: disattiva *Full scope allowed* e aggiungi gli stessi tre ruoli.

## In locale

Con `podman-compose up -d` (o `docker compose`) in `infra/docker` il realm `piattaforma` si importa
con un amministratore di prova: `admin.piattaforma` / `piattaforma-demo-2026`. Al primo accesso a
<http://localhost:4200/console> chiede di configurare l'OTP: serve un'app di autenticazione sul telefono.

## Problemi frequenti

| Sintomo | Causa e soluzione |
|---------|-------------------|
| *Accesso amministrativo non consentito da questa rete* | `ADMIN_ALLOWED_IPS` non comprende la rete da cui ti colleghi: aggiungi l'indirizzo nel `.env` e `docker compose up -d caddy` (o svuota la voce) |
| La console dice *Operazione non consentita* | L'utente non ha il ruolo `platform-admin` nel realm `piattaforma` |
| Utenti: *Gestione utenti non configurata* o errore 502 | Client di servizio mancante o segreto diverso dal `.env`: vedi i log `docker compose logs keycloak-setup` e la sezione «Installazioni esistenti» |
| Codice OTP rifiutato | L'orologio del telefono non è sincronizzato; in alternativa, dalla console di Keycloak (realm `piattaforma` → utente → *Credentials*) elimina l'OTP: al prossimo accesso si riconfigura |
| *Raggiunto il numero massimo di utenti* | Aumenta *Utenti massimi* nella licenza o disattiva un utente |
