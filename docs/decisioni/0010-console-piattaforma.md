# ADR 0010 — Console di amministrazione della piattaforma

- Stato: accettata
- Data: 2026-11-24

## Contesto

Creare un'azienda richiedeva SQL sul server e la console di Keycloak per gli utenti, copiando a mano
il `tenant_id`: lento, soggetto a errori, e affidato a strumenti con troppi poteri. Serve
un'applicazione amministrativa semplice per aziende, licenze e utenti, **inaccessibile ai dipendenti
delle aziende**, con un livello di sicurezza alto.

## Decisione

**Identità separata**
- Realm Keycloak `piattaforma` per gli amministratori della piattaforma: ruolo `platform-admin`,
  client pubblico `gestionale-console` (PKCE) con audience `gestionale-platform`.
- Flusso di login `browser-otp-obbligatoria`: password **e** OTP sempre richiesti (configurazione
  al primo accesso); password ≥ 14 caratteri, blocco dopo 5 tentativi, sessioni brevi, eventi per un anno.

**API**
- Catena di sicurezza dedicata a `/api/platform/**` (ordine 1): decoder dell'issuer del realm
  `piattaforma` con audience `gestionale-platform`; autorità `platform:admin` solo con il ruolo e
  l'issuer attesi. I token del realm delle aziende vengono rifiutati, e i token della piattaforma
  non aprono le API delle aziende (nessun `tenant_id`).
- Accesso ai dati di tutte le aziende con `WorkerContext` (`app.worker = 'platform-admin'`) e policy
  RLS dedicate **solo** su `tenants`, `licenses` e `platform_audit` (migrazione V5): la RLS resta
  attiva e le tabelle di dominio restano invisibili.
- Registro `platform_audit`: chi, cosa, su quale azienda, quando; mai password.
- Stato dell'azienda applicato: un'azienda non attiva viene bloccata nel `TenantFilter` (cache di 30 s).
- Limite `max_users` della licenza applicato alla creazione degli utenti.

**Gestione utenti**
- Interfaccia `IdentityAdmin` con l'implementazione `KeycloakIdentityAdmin` (Admin REST API):
  **unica eccezione** alla regola «solo claim OIDC neutri», isolata dietro l'interfaccia così il
  provider resta sostituibile.
- Client riservato `gestionale-platform-admin` nel realm `gestionale`: service account con
  `view-users`, `query-users`, `manage-users` (niente *full scope*, scope `kc-admin-api` richiesto da
  Keycloak 26.8.2), segreto solo nel `.env` (`PLATFORM_KEYCLOAK_CLIENT_SECRET`). Può gestire solo gli
  utenti del realm delle aziende: non i client, non gli altri realm.
- Ogni operazione verifica che l'utente appartenga all'azienda (`tenant_id`). Password provvisorie
  generate dal server (16 caratteri), mostrate una volta, mai salvate; disattivazione e reset chiudono
  le sessioni.
- Installazioni esistenti: il servizio compose `keycloak-setup` (idempotente, `kcadm`) crea o aggiorna
  il client; il Deploy riavvia Keycloak quando cambiano i realm.

**Interfaccia**
- Area `/console` nella stessa web app (stessa build, regola del codice unico), con un client OIDC e
  una sessione propri; non disponibile nell'app desktop. L'interceptor dell'app non invia mai il token
  dei dipendenti a `/api/platform`.
- Console **solo online**: niente outbox (eccezione documentata alla regola delle scritture offline:
  operazioni amministrative immediate e non riproducibili offline).
- Facoltativo: `ADMIN_ALLOWED_IPS` limita in Caddy console, login degli amministratori, console di
  Keycloak e `/api/platform` a reti note.

## Conseguenze

- Le aziende si creano e si gestiscono senza SQL; il `tenant_id` non si copia più a mano.
- L'utente `admin` del realm master resta (lo usa `keycloak-setup`): password casuale nel `.env`,
  proteggibile con `ADMIN_ALLOWED_IPS`.
- Il codice della console è scaricabile da chiunque come parte della web app: non contiene segreti e
  la protezione è interamente nell'API e in Keycloak.
- Chiusura definitiva di un'azienda (cancellazione dei dati) non è nella console: resta una procedura
  manuale documentata, per evitare cancellazioni accidentali.

## Alternative scartate

- **Ruolo «superadmin» nel realm delle aziende**: un errore di configurazione darebbe a un dipendente
  l'accesso a tutte le aziende; un realm separato isola gli account.
- **Console di Keycloak per gli utenti**: troppi poteri, nessun legame con licenze e aziende.
- **Applicazione separata** (altra build e immagine): più sicura solo in apparenza (la protezione è
  nell'API), ma il doppio da mantenere e rilasciare.
- **Credenziali dell'amministratore di Keycloak nell'API**: poteri su tutto Keycloak; il service
  account ha solo la gestione utenti di un realm.
