# Installare civil beefy su Oracle Cloud (Always Free)

Risultato: civil beefy raggiungibile da chiunque su `https://<tuo-dominio>`, con HTTPS automatico,
e rilasci con un clic da GitHub. Tutto nel piano gratuito di Oracle Cloud.

```
Internet ──HTTPS──▶ Caddy ──┬── /       ▶ web (Angular, nginx)
 (80/443)                   ├── /api/*  ▶ api (Spring Boot) ──┐
                            └── /sso/*  ▶ keycloak (login) ───┴──▶ PostgreSQL
```

Tutto gira su una sola macchina virtuale con Docker; i file sono in `infra/deploy/oracle/`.
Su un VPS di un altro fornitore (Aruba, OVH, Hetzner…) gli stessi file e lo stesso workflow
funzionano uguali: guida [deploy-vps.md](deploy-vps.md).
Rilasci: a ogni merge su `main` il workflow **Release immagini** pubblica le immagini su GitHub
(GHCR); il workflow **Deploy**, avviato a mano, le installa sulla macchina.

## 0. Cosa è gratuito e a cosa fare attenzione

- **Always Free**: macchine **Ampere A1** (ARM) fino a 4 CPU e 24 GB di RAM in totale, 200 GB di
  disco, 10 TB al mese di traffico in uscita. Per civil beefy bastano **2 CPU e 12 GB**.
- **Macchine inattive**: sugli account solo gratuiti Oracle può recuperare le macchine Always Free
  poco usate (CPU sotto il 20% per 7 giorni). Per evitarlo puoi passare l'account a
  *Pay As You Go*: le risorse Always Free restano gratuite. In quel caso imposta subito un
  **budget con avviso** (Billing → Budgets, es. 1 €) per non avere sorprese.
- **«Out of capacity»** creando una macchina A1: è frequente; riprova più tardi o scegli un altro
  *availability domain*.

## 1. Creare la macchina virtuale

> **In alternativa, da GitHub**: il workflow *Infrastruttura* crea con Terraform rete, porte, macchina
> e IP fisso, e riprova da solo ogni ora finché Oracle non ha macchine A1 libere. Configurazione in
> [`infra/terraform/oracle/README.md`](../infra/terraform/oracle/README.md); poi riprendi da qui al
> passo 3 (dominio) e, del passo 4, crea solo il file `.env`. Non mescolare le due strade sulla stessa rete.

Console Oracle Cloud → **Compute → Instances → Create instance**:

1. **Image**: *Canonical Ubuntu 24.04* (la versione per aarch64 viene scelta da sola con la shape ARM).
2. **Shape**: *Ampere* → `VM.Standard.A1.Flex`, **2 OCPU, 12 GB** di memoria.
3. **Networking**: crea una nuova VCN con *subnet pubblica* e lascia attivo *Assign a public IPv4 address*.
4. **SSH keys**: *Generate a key pair for me* e **scarica la chiave privata** (oppure carica la tua chiave pubblica).
5. **Boot volume**: 50–100 GB.
6. **Create**. Dopo qualche minuto la macchina è *Running*: annota l'**indirizzo IP pubblico**.

Consigliato: rendi l'IP fisso, così non cambia se ricrei la macchina
(*Networking → IP management → Reserved public IPs*, poi assegnalo alla VNIC della macchina).

## 2. Aprire le porte 80 e 443

Servono due passaggi: il firewall di Oracle e quello della macchina.

**Nella console**: *Networking → Virtual cloud networks →* la tua VCN *→ Security Lists → Default
Security List → Add Ingress Rules*, con *Source CIDR* `0.0.0.0/0`:

| Protocollo | Porta di destinazione | Perché |
|------------|-----------------------|--------|
| TCP | 80 | certificati Let's Encrypt e reindirizzamento a HTTPS |
| TCP | 443 | civil beefy |
| UDP | 443 | HTTP/3 (facoltativo) |

**Sulla macchina** (le immagini Ubuntu di Oracle bloccano tutto tranne SSH):

```bash
ssh -i <chiave-privata> ubuntu@<IP-pubblico>
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. Un nome a dominio

Let's Encrypt rilascia i certificati solo a un nome a dominio, non a un IP.

- **Gratis**: [DuckDNS](https://www.duckdns.org): accedi, crea un sottodominio (es. `civilbeefy`)
  e inserisci l'IP pubblico → `civilbeefy.duckdns.org`.
- **Dominio tuo** (es. `civilbeefy.it`, pochi euro l'anno): record DNS **A** che punta all'IP
  (es. `app.civilbeefy.it`). Cloudflare gestisce il DNS gratis.

Verifica dal tuo PC: `nslookup <dominio>` deve restituire l'IP della macchina.

## 4. Preparare la macchina

Sempre collegato in SSH:

```bash
# Aggiornamenti di sicurezza automatici
sudo apt update && sudo apt -y upgrade
sudo apt -y install unattended-upgrades

# Docker Engine e Docker Compose (sul server Docker va benissimo: è la tua macchina)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit
```

Ricollegati (serve per il gruppo `docker`) e prepara la cartella dell'applicazione:

```bash
ssh -i <chiave-privata> ubuntu@<IP-pubblico>
sudo mkdir -p /opt/civilbeefy && sudo chown ubuntu:ubuntu /opt/civilbeefy
cd /opt/civilbeefy
nano .env
```

Incolla il contenuto di [`infra/deploy/oracle/.env.example`](../infra/deploy/oracle/.env.example) e
compila: `APP_DOMAIN` (il dominio del passo 3), `ACME_EMAIL` (la tua email) e le **quattro password**,
ognuna generata con:

```bash
openssl rand -base64 24
```

Proteggi il file: `chmod 600 .env`. Il `.env` resta solo sulla macchina: non va mai nel repository.

## 5. Collegare GitHub alla macchina

Il workflow **Deploy** entra nella macchina con una chiave SSH dedicata. Sul tuo PC:

```bash
ssh-keygen -t ed25519 -f civilbeefy-deploy -N "" -C "github-deploy"
```

Aggiungi la chiave **pubblica** alla macchina (in SSH):

```bash
echo "<contenuto di civilbeefy-deploy.pub>" >> ~/.ssh/authorized_keys
```

Leggi l'impronta della macchina, che GitHub userà per riconoscerla (dal tuo PC):

```bash
ssh-keyscan -t ed25519 <IP-pubblico>
```

Su GitHub: repository → **Settings → Environments → New environment** → `production`. Dentro:

| Tipo | Nome | Valore |
|------|------|--------|
| Secret | `OCI_SSH_HOST` | IP pubblico della macchina |
| Secret | `OCI_SSH_USER` | `ubuntu` |
| Secret | `OCI_SSH_PRIVATE_KEY` | contenuto **intero** del file `civilbeefy-deploy` (chiave privata) |
| Secret | `OCI_SSH_KNOWN_HOSTS` | la riga stampata da `ssh-keyscan` |
| Variable | `APP_DOMAIN` | il dominio, es. `civilbeefy.duckdns.org` |
| Variable | `OCI_APP_DIR` | facoltativa, default `/opt/civilbeefy` |

Consigliato: in *Deployment protection rules* attiva **Required reviewers** (te stesso), così ogni
installazione va approvata.

## 6. Il primo rilascio

1. Le immagini devono esistere: il workflow **Release immagini** parte a ogni merge su `main`
   (Actions → *Release immagini*: deve essere verde). La prima volta puoi avviarlo a mano.
2. **Actions → Deploy → Run workflow** (lascia vuoto il campo *commit* per installare l'ultima versione).
3. Il workflow copia la configurazione, scarica le immagini, avvia i servizi e verifica
   `https://<dominio>/api/health`. La prima volta Keycloak e i certificati richiedono 1–2 minuti.
4. Apri **`https://<dominio>`**: compare la pagina di accesso di civil beefy.

## 7. Primo accesso e utenti

Il realm di produzione **non ha utenti**: si creano in Keycloak.

1. Apri `https://<dominio>/sso/admin` ed entra con `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` del `.env`.
2. **Amministratore definitivo**: nel realm *master* → *Users → Add user* (il tuo nome), password,
   *Role mapping → admin*. Esci, rientra con il nuovo utente ed **elimina l'utente temporaneo** `admin`.
3. Passa al realm **gestionale** → *Users → Add user* per ogni persona:
   - username, email, nome e cognome;
   - **Attributes**: `tenant_id` = `00000000-0000-7000-8000-00000000de00` (l'azienda demo, attiva con
     `DEMO_ENABLED=true`);
   - **Role mapping**: `admin`, `operator` oppure `viewer`;
   - **Credentials**: password con *Temporary* attivo (al primo accesso la cambiano).
4. Facoltativo: *Realm settings → Email* con l'SMTP (vedi sotto), per il «Password dimenticata?».

Per più aziende clienti servirà un'area di amministrazione dei tenant (non ancora disponibile):
per ora ogni azienda richiede una riga in `tenants` e una licenza.

## 8. Rilasci successivi e ritorno indietro

- **Nuova versione**: merge su `main` → attendi *Release immagini* verde → **Deploy → Run workflow**.
- **Tornare indietro**: **Deploy → Run workflow** con il commit precedente (40 caratteri, da
  *Commits* su GitHub). Le migrazioni del database non si annullano: un ritorno indietro va bene
  finché la versione precedente è compatibile con lo schema.
- La versione installata è `IMAGE_TAG` nel `.env` della macchina.

## 9. Email delle notifiche (facoltativo)

Servono un servizio SMTP e un mittente verificato. Gratuiti: **Brevo** (300 email al giorno) oppure
**OCI Email Delivery** (incluso in Oracle Cloud). Nel `.env` della macchina:

```
EMAIL_ENABLED=true
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USERNAME=<utente SMTP>
SMTP_PASSWORD=<chiave SMTP>
MAIL_FROM=civil beefy <noreply@tuodominio.it>
```

poi `docker compose up -d` nella cartella `/opt/civilbeefy`.

## 10. Backup del database

In SSH, un backup al giorno alle 3 con 14 giorni di storico (applicazione e Keycloak):

```bash
mkdir -p /opt/civilbeefy/backup
crontab -e
```

e aggiungi la riga:

```
0 3 * * * cd /opt/civilbeefy && docker compose exec -T db pg_dumpall -U gestionale | gzip > backup/db-$(date +\%F).sql.gz && find backup -name 'db-*.sql.gz' -mtime +14 -delete
```

Per una copia fuori dalla macchina: Object Storage di Oracle (20 GB gratuiti) con `oci os object put`.

## 11. Comandi utili sulla macchina

Nella cartella `/opt/civilbeefy`:

| Cosa | Comando |
|------|---------|
| Stato dei servizi | `docker compose ps` |
| Log (es. API) | `docker compose logs -f api` |
| Riavviare tutto | `docker compose restart` |
| Applicare modifiche al `.env` | `docker compose up -d` |
| Spazio su disco | `df -h` e `docker system df` |

## 12. App desktop

All'avvio l'app desktop chiede l'indirizzo del server: `https://<dominio>`.

## Il frontend: dove ospitarlo gratis

La scelta consigliata è quella di questa guida: **la web app sulla stessa macchina**, dietro Caddy.
È gratuita, ha un solo indirizzo (nessuna configurazione CORS, un solo certificato) e il traffico
compreso (10 TB al mese) è largamente sufficiente.

Alternative gratuite, utili più avanti:

| Opzione | Quando ha senso | Cosa cambia |
|---------|-----------------|-------------|
| **Cloudflare davanti alla macchina** (DNS con proxy, piano Free) | utenti sparsi, protezione da attacchi, cache | solo il DNS: il dominio deve essere gestito da Cloudflare |
| **Cloudflare Pages** (statico, banda illimitata) | separare la web dalla macchina | `config.json` con `apiUrl` assoluto e origine della web in `CORS_ALLOWED_ORIGINS` dell'API |
| Netlify, Vercel, GitHub Pages | come sopra | limiti di banda o uso commerciale da verificare nei loro piani gratuiti |

## Problemi frequenti

| Sintomo | Soluzione |
|---------|-----------|
| Il sito non risponde | Porte del passo 2 (security list **e** iptables); `docker compose ps` sulla macchina. |
| Errore di certificato nei primi minuti | Caddy sta ottenendo il certificato: attendi; `docker compose logs caddy`. Serve che il dominio punti già all'IP e che la porta 80 sia aperta. |
| Deploy: «Immagine … non trovata» | *Release immagini* non è ancora terminato (o è fallito) per quel commit. |
| Deploy: errore SSH «Host key verification failed» | `OCI_SSH_KNOWN_HOSTS` non corrisponde: ripeti `ssh-keyscan` (es. dopo aver ricreato la macchina). |
| Login: «Invalid parameter: redirect_uri» | `APP_DOMAIN` nel `.env` diverso dall'indirizzo con cui apri il sito. |
| Dopo l'accesso: «Nessuna azienda associata» o errore 403 | L'utente non ha l'attributo `tenant_id` o l'azienda non esiste (`DEMO_ENABLED=true` per quella demo). |
