# Avvio locale di civil beefy — mini guida

Tutto si avvia con **podman-compose**, senza alcun componente Docker. Gli stessi comandi funzionano
anche con `podman compose` e con `docker compose` (basta sostituire il comando).
I comandi valgono per PowerShell, bash e zsh.

## Una volta sola

1. **Podman e podman-compose**:
   - Windows: `winget install RedHat.Podman`, poi `podman machine init`; per podman-compose serve
     Python: `winget install Python.Python.3.13`, poi (in un nuovo terminale) `pip install podman-compose`;
   - macOS: `brew install podman podman-compose`, poi `podman machine init`;
   - Linux: pacchetti `podman` e `podman-compose` della distribuzione (o `pip install podman-compose`);
   - controlla con `podman-compose --version` (serve la versione 1.6 o successiva).

   > **Perché podman-compose e non `podman compose`?** `podman compose` non ha un motore suo:
   > chiama un programma esterno, che può essere `docker-compose` (lo strumento di Docker, che
   > però parla con Podman) oppure `podman-compose` (comunità Podman, usa solo i comandi `podman`).
   > Se Docker è bloccato sul tuo PC usa direttamente `podman-compose`; per sapere quale programma
   > usa `podman compose`, guarda la prima riga di `podman compose version`.
2. **File delle impostazioni**, dalla radice del repository:

   ```bash
   cd infra/docker
   copy .env.example .env          # macOS/Linux: cp .env.example .env
   ```

   Le password del `.env` di esempio sono solo per lo sviluppo e coincidono con quelle che l'API
   usa di default: non serve cambiare niente.

Su Windows e macOS, a ogni riavvio del PC: `podman machine start`.

## Modalità A — sviluppo (consigliata)

In container solo i servizi di supporto (database, Keycloak, Mailpit; `db-init` prepara il ruolo
del database e termina); API e web li avvii tu, con ricarica automatica del codice.
Servono Java 21 e Node.js 22.

```bash
cd infra/docker
podman-compose up -d
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
podman-compose --profile app up -d --build
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
| Stato dei servizi | `podman-compose --profile app ps` |
| Log di un servizio | `podman-compose logs -f keycloak` (o `db`, `api`, `web`, `mailpit`) |
| Fermare tutto (i dati restano) | `podman-compose --profile app down` |
| Fermare e **cancellare i dati** del database | `podman-compose --profile app down -v` |

Keycloak non conserva dati: a ogni nuovo container reimporta realm, utenti demo e tema dai file in
`infra/keycloak`. Dopo un `git pull` che li modifica basta `podman-compose up -d --force-recreate keycloak`.

## Problemi frequenti

| Sintomo | Soluzione |
|---------|-----------|
| `podman-compose` non trovato (Windows) | Riapri il terminale dopo `pip install`. Se ancora non lo trova, usa `py -m podman_compose …` al posto di `podman-compose …`, oppure aggiungi al PATH la cartella `Scripts` di Python indicata da pip. |
| `podman-compose` dice che non trova `podman` o non riesce a collegarsi | Avvia la macchina Podman: `podman machine start`, poi `podman info` deve rispondere. |
| `required variable POSTGRES_PASSWORD is missing` | Manca `infra/docker/.env`: copia `.env.example` (vedi sopra) ed esegui i comandi da `infra/docker`. |
| L'API si ferma con `password authentication failed` | Il ruolo dell'app (`gestionale_app`) lo crea e lo riallinea al `.env` il servizio `db-init` a ogni `podman-compose up -d`: controlla che risulti `Exited (0)` (`podman-compose logs db-init`). Se è la password del proprietario (`gestionale`) a non coincidere, il volume è stato creato con un `.env` diverso: `podman-compose --profile app down -v` e riparti (cancella i dati di prova). |
| Porta già in uso (5432, 8080, 8180, 4200…) | Un altro programma o un vecchio container la occupa: `podman ps -a` e rimuovi i container avviati a mano (`podman rm -f gestionale-db gestionale-keycloak gestionale-mailpit`). |
| Login: «Servizio di accesso non raggiungibile» | Keycloak non è ancora pronto (serve circa un minuto) o non è avviato: `podman-compose logs keycloak`. |
| Keycloak: «Invalid parameter: redirect_uri» | La web app non è su `http://localhost:4200` o `GESTIONALE_WEB_URL` nel `.env` è diverso. |
| `db-init` in `Exited (1)` | Il database non era ancora pronto o le password del `.env` non coincidono con quelle del volume: guarda `podman-compose logs db-init`, poi `podman-compose up -d`. Non dipende più dai fine riga di Windows: il comando è scritto in `compose.yml`. |
| La pagina mostra ancora il vecchio logo o colori | Cache del browser: Ctrl+F5 o finestra anonima. |
| Modalità A: `npm start` fallisce dopo un `git pull` | Esegui `npm ci` in `apps/web`. |
