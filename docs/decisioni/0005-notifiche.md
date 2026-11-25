# ADR 0005 — Notifiche e comunicazioni ai dipendenti

- **Stato**: accettata (primo livello: portale + email)
- **Data**: 2026-11-23

## Contesto

I dipendenti devono ricevere notifiche nel portale. Requisiti raccolti:

- **mittenti**: il sistema (eventi del gestionale) e gli amministratori (comunicazioni);
- **destinatari**: una persona, un ruolo, tutta l'azienda;
- **presa visione**: per alcune comunicazioni serve registrare che il dipendente le ha lette e comprese;
- **primo canale esterno**: email.

Vincoli del progetto: multi-tenant con RLS, provider di identità sostituibile (nessuna API
specifica di Keycloak), funzionamento offline con outbox, circa 50 utenti per azienda.

## Decisione

### Modello dati (migrazione V4)

| Tabella | Contenuto |
|---------|-----------|
| `app_users` | rubrica locale: utenti del tenant con nome, email e ruoli, dai claim del token |
| `notifications` | il messaggio: tipo (`system`/`announcement`), titolo, testo, link interno, destinatari, `requires_ack`, `send_email`, mittente |
| `notification_recipients` | una riga per destinatario: `read_at`, `acknowledged_at` |
| `email_deliveries` | coda delle email con stato, tentativi, errore |

Tutte con `tenant_id` e RLS. Fan-out alla scrittura: anche "tutta l'azienda" sono ~50 righe.

### Rubrica locale invece delle API del provider

Per risolvere "ruolo" e "tutta l'azienda" serve sapere chi sono i dipendenti. Gli utenti stanno
in Keycloak, ma il codice non deve dipendere dalle sue API: la rubrica si aggiorna a ogni
accesso (`GET /api/me`) con i claim neutri del token (`sub`, `name`, `email`, `roles`).

Conseguenza: chi non ha mai effettuato l'accesso non è in rubrica. Per non perdere le
comunicazioni obbligatorie, **al primo accesso** un utente riceve quelle con presa visione
indirizzate a tutta l'azienda o a uno dei suoi ruoli (es. un neoassunto riceve le regole aziendali).
Le comunicazioni solo informative non vengono recapitate a posteriori.

### Presa visione

- Distinta dalla lettura: aprire la notifica la segna come letta; la presa visione richiede il
  pulsante esplicito «Confermo di aver letto e compreso».
- Si registra l'**ora del server** alla ricezione della conferma; è idempotente (resta la prima).
- L'amministratore vede per ogni comunicazione chi l'ha letta, chi ha confermato e lo stato dell'email.

### Canali

1. **Portale** (fatto): campanella con contatore (aggiornato ogni minuto), elenco, dettaglio,
   banner in home per le comunicazioni da confermare; copia locale per l'offline; "letta" e
   "presa visione" passano dalla outbox.
2. **Email** (fatto): coda `email_deliveries` svuotata da `EmailDispatcher` ogni 30 secondi, con
   tentativi e attesa crescente, sicuro con più istanze (`FOR UPDATE SKIP LOCKED`). Il testo può
   escludere il corpo del messaggio (`EMAIL_INCLUDE_BODY=false`) per le comunicazioni riservate;
   il link porta alla notifica nel portale, dove avviene la presa visione (mai dall'email).
3. **Tempo reale** (da fare): SSE con LISTEN/NOTIFY di PostgreSQL al posto del polling.
4. **Push** (da fare): Web Push per la PWA, notifiche native nell'app desktop, push mobile.
   Nel payload solo "hai una nuova notifica", il contenuto si legge dal server.

### Processo di invio e RLS

Il dispatcher serve tutti i tenant. Invece di disattivare la RLS, la tabella `email_deliveries`
ha una seconda policy che la rende visibile solo a una connessione che dichiara
`app.worker = 'email-dispatcher'` (`WorkerContext`). Le tabelle di dominio restano filtrate
solo per tenant: il dispatcher non vede notifiche né utenti, solo la coda email.

Stesso schema per il controllo del rapportino mancante (#54, `MissingDailyReportWatchdog`): un
worker dedicato (`daily-report-watchdog`) ha una policy di sola lettura sulla sola tabella
`tenants`, per elencare le aziende attive. Le commesse e i rapportini restano filtrati per
tenant: il controllo gira un'azienda alla volta nel suo contesto (`TenantContext`), non con la
RLS disattivata.

### Permessi

- Ogni utente vede e aggiorna solo le proprie notifiche.
- Inviare comunicazioni, vedere i report e la rubrica: permesso `notifications:send` (ruolo `admin`).
- Il codice di dominio crea notifiche di sistema con `NotificationService.send(NewNotification.system(...))`.

## Conseguenze

- Cambi di ruolo successivi al primo accesso non riassegnano le comunicazioni già inviate.
- Le preferenze per utente (quali tipi ricevere e su quali canali) non ci sono ancora.
- Conservazione: definire per quanto tempo tenere notifiche e prese visione (valore legale
  delle conferme, GDPR) e un'eventuale esportazione del report.
- In produzione l'invio email richiede un SMTP dell'azienda (`SPRING_MAIL_*`, `MAIL_FROM`) e
  `APP_PUBLIC_URL` per i link.
- Possibile evoluzione: sincronizzare la rubrica dal provider (SCIM o Admin API dietro
  un'interfaccia) per raggiungere anche chi non ha mai effettuato l'accesso.

## Alternative scartate

- **Leggere utenti e ruoli dall'Admin API di Keycloak**: lega il prodotto a Keycloak.
- **Destinatari risolti alla lettura** (nessun fan-out): rende impossibile il report "chi deve
  ancora confermare" e complica le query per i 50 utenti attesi.
- **Disattivare la RLS per il dispatcher** (`BYPASSRLS` o funzioni `SECURITY DEFINER`):
  aprirebbe tutte le tabelle a un processo che ha bisogno di una sola coda.
