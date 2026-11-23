# Avvio locale di civil beefy — mini guida

Tutto si avvia con **Podman Compose** (va bene anche Docker: sostituisci `podman` con `docker`).
I comandi valgono per PowerShell, bash e zsh.

## Una volta sola

1. **Podman** con il supporto a compose:
   - Windows: `winget install RedHat.Podman` (oppure Podman Desktop), poi `podman machine init`;
   - macOS: `brew install podman`, poi `podman machine init`;
   - controlla che `podman compose version` risponda. Se dice che manca il provider, installa
     Docker Compose (`winget install Docker.DockerCompose`) oppure `pip install podman-compose`.
2. **File delle impostazioni**, dalla radice del repository:

   ```bash
   cd infra/docker
   cp .env.example .env
   ```

   Le password del `.env` di esempio sono solo per lo sviluppo e coincidono con quelle che l'API
   usa di default: non serve cambiare niente.

Su Windows e macOS, a ogni riavvio del PC: `podman machine start`.

## Modalità A — sviluppo (consigliata)

In container solo i servizi di supporto; API e web li avvii tu, con ricarica automatica del codice.
Servono Java 21 e Node.js 22.

```bash
cd infra/docker
podman compose up -d
```

Poi, in due terminali separati:

```bash
cd apps/api
./mvnw spring-boot:run           # PowerShell: .\mvnw.cmd spring-boot:run
```

```bash
cd apps/web
npm ci                           # solo la prima volta o dopo un git pull
npm start
```

## Modalità B — tutto in container

Per provare l'app senza installare Java e Node: compose costruisce anche API e web.

```bash
cd infra/docker
podman compose --profile app up -d --build
```

La prima volta la build richiede qualche minuto (scarica Maven, le dipendenze e i pacchetti npm).
Dopo un `git pull` ripeti lo stesso comando per ricostruire le immagini.

## Dove trovare cosa

| Cosa | Indirizzo |
|------|-----------|
| **civil beefy** | <http://localhost:4200> |
| Email inviate (Mailpit) | <http://localhost:8025> |
| Keycloak (amministrazione) | <http://localhost:8180/admin> — `admin` / `admin` |
| API (verifica) | <http://localhost:8080/api/health> |
| Database | `localhost:5432`, database `gestionale`, utente `gestionale` / `gestionale` |

Utenti demo, password `demo-password` per tutti:

| Utente | Ruolo | Cosa può fare |
|--------|-------|---------------|
| `admin.demo` | Amministratore | tutto, comprese le comunicazioni ai dipendenti |
| `operatore.demo` | Operatore | clienti (lettura e inserimento), assistente |
| `lettore.demo` | Consultazione | clienti in sola lettura |

Per provare più utenti insieme usa una finestra anonima o un altro browser: nello stesso browser
Keycloak riconnette l'utente già entrato.

## Fermare, ripartire, azzerare

Sempre dalla cartella `infra/docker`:

| Cosa | Comando |
|------|---------|
| Stato dei servizi | `podman compose --profile app ps` |
| Log di un servizio | `podman compose logs -f keycloak` (o `db`, `api`, `web`, `mailpit`) |
| Fermare tutto (i dati restano) | `podman compose --profile app down` |
| Fermare e **cancellare i dati** del database | `podman compose --profile app down -v` |

Keycloak non conserva dati: a ogni nuovo container reimporta realm, utenti demo e tema dai file in
`infra/keycloak`. Dopo un `git pull` che li modifica basta `podman compose up -d --force-recreate keycloak`.

## Problemi frequenti

| Sintomo | Soluzione |
|---------|-----------|
| `required variable POSTGRES_PASSWORD is missing` | Manca `infra/docker/.env`: copia `.env.example` (vedi sopra) ed esegui i comandi da `infra/docker`. |
| L'API si ferma con `password authentication failed` | Il volume del database è stato creato con password diverse da quelle attuali del `.env`. PostgreSQL le legge solo al primo avvio: `podman compose --profile app down -v` e riparti (cancella i dati di prova). |
| Porta già in uso (5432, 8080, 8180, 4200…) | Un altro programma o un vecchio container la occupa: `podman ps -a` e rimuovi i container avviati a mano (`podman rm -f gestionale-db gestionale-keycloak gestionale-mailpit`). |
| Login: «Servizio di accesso non raggiungibile» | Keycloak non è ancora pronto (serve circa un minuto) o non è avviato: `podman compose logs keycloak`. |
| Keycloak: «Invalid parameter: redirect_uri» | La web app non è su `http://localhost:4200` o `GESTIONALE_WEB_URL` nel `.env` è diverso. |
| Container del database in `Exited (127)`, «required file not found» | Fine riga Windows negli script: vedi «Problemi frequenti» nel README principale. |
| La pagina mostra ancora il vecchio logo o colori | Cache del browser: Ctrl+F5 o finestra anonima. |
| Modalità A: `npm start` fallisce dopo un `git pull` | Esegui `npm ci` in `apps/web`. |
