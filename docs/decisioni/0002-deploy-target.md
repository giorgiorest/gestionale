# ADR 0002 — Target di deploy: Oracle Cloud Always Free

- **Stato**: accettata (sostituisce la versione «aperta» con le domande iniziali)
- **Data**: 2026-11-23

## Contesto

civil beefy deve essere raggiungibile da altre persone, a costo zero nella fase iniziale, con
rilasci ripetibili da GitHub. Le domande aperte erano: provider, metodo, segreti, ambienti.

## Decisione

1. **Provider**: Oracle Cloud Infrastructure, piano **Always Free**: una macchina virtuale
   **Ampere A1** (arm64, 2 OCPU e 12 GB consigliati), Ubuntu 24.04, dati in UE scegliendo una
   region europea.
2. **Metodo**: Docker Compose sulla macchina (`infra/deploy/oracle/compose.yml`), con:
   - **Caddy** come unico ingresso (80/443), HTTPS automatico con Let's Encrypt;
   - **PostgreSQL** unico, con database `gestionale` (applicazione) e `keycloak`;
   - **Keycloak** in modalità produzione sotto `https://<dominio>/sso` (un solo nome a dominio,
     un solo certificato, compatibile con i domini gratuiti come DuckDNS);
   - **API** e **web** dalle immagini GHCR del commit da installare.
   - `db-init` crea i ruoli del database (applicazione non superuser, Keycloak) e riallinea le password.
3. **Immagini**: `release-images.yml` a ogni push su `main` pubblica API e web per **amd64 e arm64**
   con etichetta `sha-<commit>` (coppia coerente per ogni commit) e `latest`. La compilazione
   (jar, Angular) avviene una sola volta sulla piattaforma della build (`--platform=$BUILDPLATFORM`).
4. **Rilascio**: workflow **Deploy** manuale (environment GitHub `production`, approvazione
   facoltativa): copia via SSH compose, Caddyfile, realm e tema; scarica le immagini con il token
   temporaneo del job (nessuna credenziale GitHub resta sulla macchina); riavvia; verifica
   `/api/health`. Ritorno indietro = deploy di un commit precedente.
5. **Segreti**:
   - password e configurazione dell'installazione solo nel `.env` della macchina (permessi 600);
   - accesso SSH e impronta della macchina negli **environment secrets** di GitHub;
   - nessun segreto nel repository.
6. **Frontend**: sulla stessa macchina (gratuito, stesso dominio dell'API). Alternative gratuite
   documentate: Cloudflare come proxy/CDN, Cloudflare Pages per i soli file statici.

## Conseguenze

- Un solo ambiente (produzione/demo). Uno *staging* si ottiene con una seconda macchina
  Always Free e un secondo environment GitHub con la stessa pipeline.
- Le macchine Always Free inattive possono essere recuperate da Oracle sugli account solo gratuiti:
  per un uso continuativo passare a Pay As You Go con un budget di avviso.
- Backup: `pg_dumpall` giornaliero sulla macchina (guida); copia esterna su Object Storage da attivare.
- Migrazioni del database non reversibili: il ritorno indietro richiede versioni compatibili con lo schema.
- Le installazioni presso clienti (on-premise o cloud dedicato) possono riusare lo stesso
  `infra/deploy/oracle/compose.yml`, che non dipende da Oracle.

## Aggiornamento — VPS a pagamento (novembre 2026)

Le macchine A1 gratuite di Oracle nella regione di casa (Torino) risultavano spesso esaurite.
La produzione può quindi stare anche su un **VPS a pagamento** (es. Aruba VPS H.4-8: 4 vCPU, 8 GB,
data center in Italia, circa 5–11 € al mese), con **gli stessi file e lo stesso workflow Deploy**:
servono solo SSH, Docker e il `.env` sulla macchina. Guida: [deploy-vps.md](../deploy-vps.md).

- L'utente della macchina è sempre `ubuntu` (creato a mano dove il fornitore dà solo `root`);
  accesso solo con chiave, firewall `ufw` con 22, 80 e 443.
- Il workflow Deploy controlla la macchina prima di installare (Docker utilizzabile, `.env`
  presente, `APP_DOMAIN` coerente con l'environment, `ACME_EMAIL` valida, spazio libero) e valida
  la configurazione con `docker compose config` prima di scaricare le immagini.
- Terraform e i tentativi automatici (ADR 0007, poi rimossi) erano specifici di Oracle Cloud; su un VPS la
  macchina si crea dal pannello del fornitore.

## Alternative scartate

- **Kubernetes (anche OKE gratuito)**: complessità sproporzionata per una macchina e ~50 utenti per cliente.
- **PaaS gratuiti** (Render, Fly.io, Railway): limiti di memoria o di sospensione incompatibili con
  Keycloak e Spring Boot sempre attivi.
- **Keycloak su un sottodominio separato**: richiede un secondo nome DNS e certificato; rimandato
  a quando ci sarà un dominio proprio.
