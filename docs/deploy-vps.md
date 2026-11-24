# Installare Civil Beefy su un VPS (Aruba, OVH, Hetzner…)

Risultato: Civil Beefy su `https://<tuo-dominio>`, con HTTPS automatico. Ogni merge su `main`
costruisce tutto e installa in produzione **dopo la tua approvazione**. Vale per qualsiasi server
Linux raggiungibile via SSH; per creare la macchina su Oracle Cloud c'è
[deploy-oracle-cloud.md](deploy-oracle-cloud.md).

```
Internet ──HTTPS──▶ Caddy ──┬── /       ▶ web (Angular, nginx) + installer dell'app desktop
 (80/443)                   ├── /api/*  ▶ api (Spring Boot) ──┐
                            └── /sso/*  ▶ keycloak (login) ───┴──▶ PostgreSQL
```

I file di produzione sono in `infra/deploy/oracle/` (il nome è storico: valgono per ogni server).

## Il percorso in breve

| # | Passo | Dove | Chi |
|---|-------|------|-----|
| 1 | Chiavi SSH | PC | a mano, una volta |
| 2 | Ordinare il VPS | pannello del fornitore | a mano |
| 3 | Utente `ubuntu` con le chiavi | VPS, come root | a mano, un comando |
| 4 | Dominio | DuckDNS o DNS | a mano |
| 5 | Environment `production` | GitHub | a mano, una volta |
| 6 | **Prepara server**: aggiornamenti, firewall, SSH, Docker, `.env`, backup | GitHub Actions | workflow |
| 7 | **Rilascio**: build, immagini, installer desktop (automatico a ogni merge) → **Deploy** avviato da te | GitHub Actions | un clic per ogni rilascio |

## 0. Che macchina scegliere

| | Minimo | Consigliato |
|---|---|---|
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GB (prove, pochi utenti) | **8 GB** |
| Disco | 40 GB | 80 GB |
| Sistema | Ubuntu 24.04 LTS (va bene anche 22.04) | |

Esempi (prezzi IVA esclusa, novembre 2026): **Aruba VPS H.4-8** (4 vCPU, 8 GB, 80 GB, data center
in Italia), **OVHcloud VPS-2** (4 vCore, 8 GB, 75 GB, Milano). Le immagini sono pubblicate per x86
(amd64) e ARM (arm64): va bene qualsiasi processore. Scelte e alternative:
[ADR 0002](decisioni/0002-deploy-target.md).

## 1. Chiavi SSH sul tuo PC

Due chiavi (PowerShell su Windows):

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\civilbeefy" -C "civilbeefy"
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\civilbeefy-deploy" -C "github-deploy"
```

- `civilbeefy`: **la tua**, per collegarti. Proteggila con una passphrase e salvala in un password
  manager (se la dimentichi: [Problemi frequenti](#problemi-frequenti)).
- `civilbeefy-deploy`: per GitHub (Prepara server e Deploy). **Senza passphrase** (Invio due volte).

Per non riscrivere la passphrase a ogni collegamento, agente SSH di Windows (PowerShell come
amministratore, una volta: `Get-Service ssh-agent | Set-Service -StartupType Automatic; Start-Service ssh-agent`;
poi, normale: `ssh-add "$env:USERPROFILE\.ssh\civilbeefy"`).

Sul server vanno solo le chiavi **pubbliche** (`.pub`); le private non si condividono mai.

## 2. Ordinare il VPS

- **Sistema operativo**: Ubuntu Server 24.04 LTS, senza applicazioni o pannelli preinstallati.
- **Chiave SSH**: se il modulo la chiede, incolla `civilbeefy.pub`
  (`Get-Content "$env:USERPROFILE\.ssh\civilbeefy.pub"`). **Aruba** la rifiuta se c'è il commento
  finale con uno spazio: incolla solo `ssh-ed25519 AAAA…`.
- **Password di root** (Aruba): robusta, nel password manager; servirà solo in emergenza.
- **Servizi aggiuntivi** (firewall a pagamento, storage, switch): non servono.
- **Data center**: in Italia o nell'UE.

Annota l'**indirizzo IPv4** del VPS.

## 3. Utente `ubuntu` con le chiavi

Collegati come root (su OVH l'utente iniziale è già `ubuntu`: usa `ubuntu@` e `sudo` davanti ai comandi):

```powershell
ssh -i "$env:USERPROFILE\.ssh\civilbeefy" root@<IP>
```

Crea l'utente `ubuntu` (sudo senza password, serve a Prepara server) con la tua chiave e quella del
deploy. Sostituisci `<CHIAVE DEPLOY>` con il contenuto di `civilbeefy-deploy.pub`:

```bash
id ubuntu > /dev/null 2>&1 || adduser --disabled-password --gecos "" ubuntu; usermod -aG sudo ubuntu && echo 'ubuntu ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/90-ubuntu && chmod 440 /etc/sudoers.d/90-ubuntu && mkdir -p /home/ubuntu/.ssh && cat /root/.ssh/authorized_keys > /home/ubuntu/.ssh/authorized_keys && echo "<CHIAVE DEPLOY>" >> /home/ubuntu/.ssh/authorized_keys && chown -R ubuntu:ubuntu /home/ubuntu/.ssh && chmod 700 /home/ubuntu/.ssh && chmod 600 /home/ubuntu/.ssh/authorized_keys
```

Poi leggi l'**impronta del server**, che GitHub userà per riconoscerlo (sostituisci l'IP):

```bash
echo "<IP> $(cut -d' ' -f1,2 /etc/ssh/ssh_host_ed25519_key.pub)"
```

Verifica da un'altra finestra: `ssh -i "$env:USERPROFILE\.ssh\civilbeefy" ubuntu@<IP>`.

## 4. Dominio

Let's Encrypt rilascia certificati solo a un nome a dominio:

- **Gratis**: [DuckDNS](https://www.duckdns.org) → crea il sottodominio (es. `civilbeefy`), metti
  l'IP del VPS in *current ip* e premi *update ip*.
- **Dominio tuo**: record **A** verso l'IP del VPS.

Verifica con un DNS pubblico: `nslookup civilbeefy.duckdns.org 1.1.1.1` deve rispondere con l'IP.
Se senza `1.1.1.1` risponde *Non-existent domain*, è il DNS della rete del PC (es. aziendale): il
dominio funziona comunque; per aprire il sito usa un'altra rete (es. l'hotspot del telefono).

## 5. GitHub: environment e variabili

Repository → *Settings → Environments → New environment* → **`production`**:

| Tipo | Nome | Valore |
|------|------|--------|
| Secret | `OCI_SSH_HOST` | IP del VPS |
| Secret | `OCI_SSH_USER` | `ubuntu` |
| Secret | `OCI_SSH_PRIVATE_KEY` | contenuto **intero** di `civilbeefy-deploy` (senza `.pub`): `Get-Content "$env:USERPROFILE\.ssh\civilbeefy-deploy" \| Set-Clipboard` |
| Secret | `OCI_SSH_KNOWN_HOSTS` | la riga dell'impronta del passo 3 |
| Variable | `APP_DOMAIN` | es. `civilbeefy.duckdns.org` |
| Variable | `ACME_EMAIL` | la tua email, per Let's Encrypt (avvisi sui certificati) |
| Variable | `OCI_APP_DIR` | facoltativa, default `/opt/civilbeefy` |

I nomi `OCI_*` sono storici e valgono per qualsiasi server. Se `OCI_SSH_HOST` è un nome a dominio,
anche la riga dell'impronta deve iniziare con lo stesso nome.

Nella stessa pagina, **Deployment branches and tags** → *Selected branches* → `main`: il deploy
parte solo dalla versione ufficiale.

**Approvazione dei deploy.** In un repository **privato** GitHub offre i *Required reviewers* degli
environment solo con il piano Enterprise. Al loro posto, a ogni rilascio il job **approvazione** apre
un'issue «Rilascio pronto» (ti arrivano email e notifica, anche sull'app GitHub del telefono) e aspetta:

- commenti **`/deploy`** → il deploy parte nello stesso run;
- commenti **`/annulla`** → la produzione resta com'è;
- nessuna risposta entro **30 minuti** → la richiesta scade (avvia **Deploy** a mano quando vuoi).

Conta solo il commento di chi ha accesso in scrittura al repository. L'attesa consuma minuti di
GitHub Actions (uno al minuto): per cambiarne la durata, variabile di repository
`DEPLOY_APPROVAL_MINUTES`. Un rilascio più recente sostituisce quello in attesa.

Con un piano che offre i *Required reviewers* (repository pubblico o Enterprise) si può invece usare
`AUTO_DEPLOY` = `true` con i revisori su `production`. **Mai `AUTO_DEPLOY` senza Required reviewers**:
installerebbe senza conferma.

## 6. Prepara server

**Actions → Prepara server → Run workflow** (approva quando richiesto). Sulla macchina, come root
via `sudo`, il workflow:

1. installa aggiornamenti e attiva quelli di sicurezza automatici (attende quelli già in corso);
2. imposta il fuso orario Europe/Rome;
3. firewall `ufw`: solo 22, 80, 443 (TCP e UDP);
4. SSH **solo con chiave**, niente accesso di root;
5. installa Docker e Compose, mette `ubuntu` nel gruppo `docker`;
6. crea `/opt/civilbeefy` e il **`.env` con password casuali**, solo se non esiste già (le password
   nascono sulla macchina e non passano mai da GitHub); in un `.env` esistente aggiunge solo le voci
   introdotte dopo (es. il segreto della console di piattaforma);
7. backup del database ogni notte alle 3 in `/opt/civilbeefy/backup`, 14 giorni di storico.

È idempotente: si può rilanciare (es. dopo aver cambiato fornitore o per rimettere in ordine la
macchina). Se gli aggiornamenti richiedono un riavvio lo segnala; rilancialo con **Riavvia** spuntato.

Per il primo accesso a Keycloak ti serve la password generata, che resta sulla macchina:

```bash
grep KEYCLOAK_ADMIN_PASSWORD /opt/civilbeefy/.env
```

Salvala nel password manager. Per cambiare altri valori (email SMTP, marchio, link legali):
`nano /opt/civilbeefy/.env`, poi `cd /opt/civilbeefy && docker compose up -d`. Significato delle
voci: [`infra/deploy/oracle/.env.example`](../infra/deploy/oracle/.env.example).

## 7. Rilasci

A ogni merge su `main`, **Release immagini**:

1. costruisce l'app desktop per Windows;
2. pubblica su GHCR le immagini di API e web (con gli installer) per amd64 e arm64;
3. apre l'issue **«Rilascio pronto»** e aspetta il tuo **`/deploy`** (vedi passo 5);
4. dopo `/deploy` esegue **Deploy** per la stessa versione, nello stesso run.

**Deploy**:

1. verifica che le immagini di quel commit esistano;
2. **controlla la macchina**: Docker utilizzabile, `.env` presente, `APP_DOMAIN` uguale a quello
   dell'environment, `ACME_EMAIL` valida, segreto della console, spazio libero;
3. copia compose, Caddyfile, realm e tema di Keycloak del commit (e riavvia Keycloak se i realm cambiano);
4. verifica la configurazione, scarica le immagini (accesso a GHCR valido solo per quel job), avvia;
5. attende che `https://<dominio>/api/health` risponda (fino a 5 minuti).

Per **tornare indietro**: **Deploy** con un commit precedente (40 caratteri, da *Commits* su GitHub).

La prima volta Keycloak e il certificato richiedono 1–2 minuti. Poi apri `https://<dominio>`.
L'app per Windows si scarica da `https://<dominio>/app-desktop` (link «App per Windows» nella pagina
di accesso); al primo avvio chiede l'indirizzo del server, mostrato nella stessa pagina.

## 8. Primo accesso: console di piattaforma

Aziende, licenze e utenti si gestiscono dalla **console di piattaforma** su `https://<dominio>/console`
(password + codice OTP; i dipendenti non possono entrarci). La prima volta crea il tuo account di
amministratore della piattaforma nella console di Keycloak (realm `piattaforma`), poi lavori solo
nella console: [console-piattaforma.md](console-piattaforma.md).

Per un'installazione già avviata prima della console: lancia **Prepara server** (aggiunge al `.env`
il segreto della console) e poi **Deploy**.

## 9. Dopo

| Cosa | Come |
|------|------|
| Stato, log, riavvio dei servizi | [passo 11 della guida Oracle](deploy-oracle-cloud.md#11-comandi-utili-sulla-macchina) (uguale) |
| Email delle notifiche | [passo 9 della guida Oracle](deploy-oracle-cloud.md#9-email-delle-notifiche-facoltativo) |
| Backup | ogni notte in `/opt/civilbeefy/backup`; conviene copiarli anche fuori dal VPS (es. `scp` sul tuo PC o uno storage) |
| Aggiornamenti di sistema | automatici; per un riavvio pulito: **Prepara server** con **Riavvia** |
| Nuovo server (o cambio fornitore) | passi 2–6 sul nuovo server, poi **Deploy**; i dati si portano con un backup |

## Fare a mano invece di Prepara server

Gli stessi passi, se preferisci (come `ubuntu`):

```bash
sudo apt update && sudo apt -y upgrade && sudo apt -y install unattended-upgrades
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp && sudo ufw --force enable
printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n' | sudo tee /etc/ssh/sshd_config.d/01-civilbeefy.conf && sudo systemctl reload ssh
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu
```

Il file SSH si chiama `01-…` perché `sshd` usa il **primo** valore che trova e legge i file in ordine
alfabetico: molti VPS hanno `50-cloud-init.conf` con `PasswordAuthentication yes`, che vincerebbe su
un file `99-…`. Verifica con `sudo sshd -T | grep -Ei 'passwordauthentication|permitrootlogin'`
(deve dire `no` per entrambi).

Dopo `usermod` esci e ricollegati. Per il `.env` copia
[`infra/deploy/oracle/.env.example`](../infra/deploy/oracle/.env.example) in `/opt/civilbeefy/.env`
(`chmod 600`) e genera ogni password con `openssl rand -hex 24`.

## Problemi frequenti

| Sintomo | Causa e soluzione |
|---------|-------------------|
| Il pannello rifiuta la chiave SSH | Togli il commento finale (testo dopo la chiave); in alternativa usa una chiave RSA (`ssh-keygen -t rsa -b 4096`) |
| `Enter passphrase for key` | È la passphrase della **tua** chiave: è normale |
| Passphrase dimenticata | Crea una nuova `civilbeefy` (passo 1, rispondi `y` a *Overwrite*); come root (password o console web del fornitore) metti la nuova `.pub` in `/root/.ssh/authorized_keys` e in `/home/ubuntu/.ssh/authorized_keys` al posto della vecchia (lascia la chiave del deploy) |
| `Saving key "\\civilbeefy" failed: Permission denied` | Nel comando manca `$env:USERPROFILE\.ssh\`: il file finirebbe in `C:\` |
| `Waiting for cache lock … unattended-upgr` | Aggiornamenti automatici in corso: aspetta (Prepara server attende da solo) |
| Schermate viola durante gli aggiornamenti | **Invio** sul valore proposto (Prepara server non le mostra) |
| `docker ps`: *permission denied … docker.sock* | L'utente è stato appena aggiunto al gruppo `docker`: esci e ricollegati |
| `ssh-keyscan`: *unsupported KEX method* | OpenSSH di Windows troppo vecchio: leggi l'impronta dalla macchina (passo 3) |
| `nslookup`: *Non-existent domain* | Prova `nslookup <dominio> 1.1.1.1`; se risponde, è il DNS della rete del PC |
| Prepara server: *deve poter usare sudo senza password* | Rifai il comando del passo 3 come root |
| Workflow: *No ED25519 host key is known* / *Host key verification failed* | `OCI_SSH_KNOWN_HOSTS` non corrisponde al server: rigenera la riga sul server (passo 3) e incollala intera, su una riga. Il workflow stampa l'impronta che ha ricevuto (*Impronta del server: SHA256:…*): sul server confrontala con `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` |
| Workflow: *indirizzo diverso da OCI_SSH_HOST* | La riga dell'impronta deve iniziare con lo stesso indirizzo scritto in `OCI_SSH_HOST` (es. entrambi l'IP) |
| Workflow: *Permission denied (publickey)* | Manca la chiave pubblica del deploy in `/home/ubuntu/.ssh/authorized_keys`, o `OCI_SSH_PRIVATE_KEY` è incompleto (deve includere le righe `BEGIN`/`END`) |
| Deploy: *Docker non è installato* / *Manca .env* | Lancia **Prepara server** |
| Deploy: *APP_DOMAIN … non è* / *ACME_EMAIL … non valido* | Correggi `/opt/civilbeefy/.env` o la variabile dell'environment |
| Il deploy non parte dopo un merge | Aspetta il tuo `/deploy` sull'issue «Rilascio pronto» (30 minuti); scaduto, avvia **Deploy** a mano |
| `/deploy` non fa partire nulla | Va scritto da un account con accesso in scrittura, sull'issue del rilascio ancora aperta |
| Non vedo *Required reviewers* nell'environment | Repository privato senza GitHub Enterprise: la funzione non c'è; si approva con `/deploy` sull'issue |
| Il deploy è partito senza chiedere approvazione | `AUTO_DEPLOY = true` senza *Required reviewers*: togli subito la variabile |
| Console: *Accesso amministrativo non consentito da questa rete* | `ADMIN_ALLOWED_IPS` nel `.env` non comprende la tua rete ([console-piattaforma.md](console-piattaforma.md)) |
| Deploy: *Manca PLATFORM_KEYCLOAK_CLIENT_SECRET* | Lancia **Prepara server**, che lo aggiunge al `.env` |
| La pagina di accesso ha l'aspetto standard di Keycloak (sfondo scuro, niente logo) | Tema non caricato: sul VPS `cd /opt/civilbeefy && docker compose restart keycloak` (dalla versione con il tema sul posto il Deploy lo fa da solo) |
| Il sito non ha il certificato | Porte 80/443 chiuse (firewall del fornitore) o DNS non aggiornato; log: `docker compose logs caddy` |
