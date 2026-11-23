# Installare civil beefy su un VPS (Aruba, OVH, Hetzner…)

Risultato: civil beefy su `https://<tuo-dominio>`, con HTTPS automatico e rilasci da GitHub con il
workflow **Deploy**. Vale per qualsiasi server Linux raggiungibile via SSH; per Oracle Cloud c'è
la guida dedicata [deploy-oracle-cloud.md](deploy-oracle-cloud.md).

```
Internet ──HTTPS──▶ Caddy ──┬── /       ▶ web (Angular, nginx)
 (80/443)                   ├── /api/*  ▶ api (Spring Boot) ──┐
                            └── /sso/*  ▶ keycloak (login) ───┴──▶ PostgreSQL
```

I file di produzione sono in `infra/deploy/oracle/` (il nome è storico: valgono per ogni server).

## 0. Che macchina scegliere

| | Minimo | Consigliato |
|---|---|---|
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GB (prove, pochi utenti) | **8 GB** |
| Disco | 40 GB | 80 GB |
| Sistema | Ubuntu 24.04 LTS (va bene anche 22.04) | |

Esempi (prezzi IVA esclusa, novembre 2026): **Aruba VPS H.4-8** (4 vCPU, 8 GB, 80 GB, data center
in Italia), **OVHcloud VPS-2** (4 vCore, 8 GB, 75 GB, Milano). Le immagini sono pubblicate per x86
(amd64) e ARM (arm64): va bene qualsiasi processore.

Scelte e alternative: [ADR 0002](decisioni/0002-deploy-target.md).

## 1. Chiavi SSH sul tuo PC

Servono due chiavi (PowerShell su Windows):

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\civilbeefy" -C "civilbeefy"
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\civilbeefy-deploy" -C "github-deploy"
```

- `civilbeefy`: **la tua**, per collegarti. Proteggila con una passphrase e salvala in un password
  manager (se la dimentichi: [Problemi frequenti](#problemi-frequenti)).
- `civilbeefy-deploy`: per il workflow Deploy. **Senza passphrase** (Invio due volte): GitHub non
  può digitarla.

Per non riscrivere la passphrase a ogni collegamento, usa l'agente SSH di Windows (PowerShell come
amministratore, una volta: `Get-Service ssh-agent | Set-Service -StartupType Automatic; Start-Service ssh-agent`;
poi, normale: `ssh-add "$env:USERPROFILE\.ssh\civilbeefy"`).

Sul server vanno solo le chiavi **pubbliche** (`.pub`). Le chiavi private non si condividono mai.

## 2. Ordinare il VPS

- **Sistema operativo**: Ubuntu Server 24.04 LTS, senza applicazioni o pannelli preinstallati.
- **Chiave SSH**: se il modulo la chiede, incolla il contenuto di `civilbeefy.pub`
  (`Get-Content "$env:USERPROFILE\.ssh\civilbeefy.pub"`). **Aruba** rifiuta la chiave se c'è il
  commento finale con uno spazio: incolla solo `ssh-ed25519 AAAA…`.
- **Password di root** (Aruba): robusta, nel password manager; servirà solo in emergenza.
- **Servizi aggiuntivi** (firewall a pagamento, storage, switch): non servono.
- **Data center**: in Italia o nell'UE.

Annota l'**indirizzo IPv4** del VPS (pannello del fornitore o email di attivazione).

## 3. Primo accesso e utente `ubuntu`

```powershell
ssh -i "$env:USERPROFILE\.ssh\civilbeefy" root@<IP>
```

(su OVH l'utente iniziale è già `ubuntu`: salta questo passo.)

Su Aruba e sui VPS che danno solo `root`, crea l'utente `ubuntu` con la tua chiave: tutte le guide
e il workflow Deploy usano questo utente, non root.

```bash
adduser --disabled-password --gecos "" ubuntu && usermod -aG sudo ubuntu && echo 'ubuntu ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/90-ubuntu && chmod 440 /etc/sudoers.d/90-ubuntu && mkdir -p /home/ubuntu/.ssh && cp /root/.ssh/authorized_keys /home/ubuntu/.ssh/ && chown -R ubuntu:ubuntu /home/ubuntu/.ssh && chmod 700 /home/ubuntu/.ssh && chmod 600 /home/ubuntu/.ssh/authorized_keys
```

Verifica da **un'altra finestra**: `ssh -i "$env:USERPROFILE\.ssh\civilbeefy" ubuntu@<IP>`.
Da qui in poi lavora sempre come `ubuntu`.

## 4. Aggiornamenti

```bash
sudo apt update && sudo apt -y upgrade && sudo apt -y install unattended-upgrades
```

- Su una macchina appena creata Ubuntu sta già aggiornando da solo: il comando ripete
  *«Waiting for cache lock … unattended-upgr»* finché non ha finito (anche 10–20 minuti).
  **Aspetta**, non interromperlo.
- Schermate viola (*character set*, *services to restart*, file di configurazione): **Invio** sul
  valore proposto.
- Alla fine: `[ -f /var/run/reboot-required ] && sudo reboot`, poi ricollegati.

## 5. Firewall e accesso SSH

Solo SSH, HTTP e HTTPS (HTTP/3 compreso):

```bash
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp && sudo ufw --force enable && sudo ufw status
```

Se il fornitore ha anche un firewall nel pannello (es. *security group*), apri lì le stesse porte.

Chiave del workflow Deploy, **aggiunta** a quella personale (`>>`, non `>`):

```bash
echo "<contenuto di civilbeefy-deploy.pub>" >> ~/.ssh/authorized_keys
```

Accesso solo con chiave e niente root (prima verifica di entrare come `ubuntu` con la chiave):

```bash
printf 'PasswordAuthentication no\nPermitRootLogin no\n' | sudo tee /etc/ssh/sshd_config.d/99-civilbeefy.conf && sudo systemctl restart ssh
```

Tieni aperta la sessione e controlla da un'altra finestra di poter ancora entrare.

## 6. Docker

```bash
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu
```

Esci (`exit`), ricollegati e verifica: `docker run --rm hello-world && docker compose version`.

## 7. Cartella e file `.env`

Crea `/opt/civilbeefy` e un `.env` con **password casuali generate sulla macchina** (non passano
mai dal tuo PC). Prima di incollare, sostituisci il dominio e `LA-TUA-EMAIL` (serve a Let's Encrypt):

```bash
sudo mkdir -p /opt/civilbeefy && sudo chown ubuntu:ubuntu /opt/civilbeefy && cd /opt/civilbeefy && gen() { openssl rand -hex 24; } && cat > .env <<EOF
APP_DOMAIN=civilbeefy.duckdns.org
ACME_EMAIL=LA-TUA-EMAIL
IMAGE_REGISTRY=ghcr.io/giorgiorest
IMAGE_TAG=latest
POSTGRES_USER=gestionale
POSTGRES_PASSWORD=$(gen)
DB_PASSWORD=$(gen)
KEYCLOAK_DB_PASSWORD=$(gen)
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=$(gen)
DEMO_ENABLED=true
EMAIL_ENABLED=false
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
MAIL_FROM=civil beefy <noreply@example.com>
BRAND_NAME=
BRAND_LOGO_URL=
BRAND_PRIMARY_COLOR=
BRAND_ACCENT_COLOR=
LEGAL_PRIVACY_URL=
LEGAL_TERMS_URL=
EOF
chmod 600 .env
```

- Significato di ogni voce: [`infra/deploy/oracle/.env.example`](../infra/deploy/oracle/.env.example).
- Per correggere un valore: `nano /opt/civilbeefy/.env`.
- Salva nel password manager la password del primo accesso a Keycloak:
  `grep KEYCLOAK_ADMIN_PASSWORD /opt/civilbeefy/.env`.
- Il `.env` resta solo sulla macchina: mai nel repository, mai in chat.

## 8. Dominio

Let's Encrypt rilascia certificati solo a un nome a dominio:

- **Gratis**: [DuckDNS](https://www.duckdns.org) → crea il sottodominio (es. `civilbeefy`), metti
  l'IP del VPS in *current ip* e premi *update ip*.
- **Dominio tuo**: record **A** verso l'IP del VPS.

Verifica con un DNS pubblico: `nslookup civilbeefy.duckdns.org 1.1.1.1` deve rispondere con l'IP.
Se senza `1.1.1.1` risponde *Non-existent domain*, è il DNS della rete (es. aziendale) a non vederlo:
il dominio funziona comunque; per aprire il sito usa un'altra rete (es. l'hotspot del telefono).

Il nome deve coincidere con `APP_DOMAIN` del `.env` e dell'environment GitHub.

## 9. Collegare GitHub

**Impronta del server** (per riconoscerlo): sulla macchina, come `ubuntu`, sostituendo l'IP:

```bash
echo "<IP> $(cut -d' ' -f1,2 /etc/ssh/ssh_host_ed25519_key.pub)"
```

Da Windows `ssh-keyscan` può fallire con *unsupported KEX method*: leggere l'impronta dalla
macchina lo evita ed è più sicuro (non dipende dalla rete).

**Environment** su GitHub: repository → *Settings → Environments → New environment* → `production`:

| Tipo | Nome | Valore |
|------|------|--------|
| Secret | `OCI_SSH_HOST` | IP del VPS |
| Secret | `OCI_SSH_USER` | `ubuntu` |
| Secret | `OCI_SSH_PRIVATE_KEY` | contenuto **intero** di `civilbeefy-deploy` (senza `.pub`): `Get-Content "$env:USERPROFILE\.ssh\civilbeefy-deploy" \| Set-Clipboard` |
| Secret | `OCI_SSH_KNOWN_HOSTS` | la riga dell'impronta (IP, `ssh-ed25519`, chiave) |
| Variable | `APP_DOMAIN` | es. `civilbeefy.duckdns.org` |
| Variable | `OCI_APP_DIR` | facoltativa, default `/opt/civilbeefy` |

I nomi `OCI_*` sono storici e valgono per qualsiasi server. Se `OCI_SSH_HOST` è un nome a dominio,
anche la riga dell'impronta deve iniziare con lo stesso nome.

Nella stessa pagina:

- **Deployment branches and tags** → *Selected branches* → `main`;
- **Required reviewers** → te stesso: ogni installazione aspetta la tua approvazione.

Se non usi Oracle Cloud: *Settings → Secrets and variables → Actions → Variables*, togli
`OCI_RETRY_ENABLED`, e in *Actions → Infrastruttura (tentativi)* → **⋯ → Disable workflow**.

## 10. Primo rilascio

**Actions → Deploy → Run workflow** (campo *commit* vuoto = l'ultima versione di `main`).

Cosa fa il workflow:

1. verifica che le immagini di quel commit esistano su GHCR (le pubblica *Release immagini* a ogni
   merge su `main`);
2. **controlla la macchina**: Docker e Compose utilizzabili da `ubuntu`, `.env` presente,
   `APP_DOMAIN` uguale a quello dell'environment, `ACME_EMAIL` valida, spazio libero;
3. copia compose, Caddyfile, realm e tema di Keycloak in `/opt/civilbeefy`;
4. verifica la configurazione (`docker compose config`), scarica le immagini con un accesso a GHCR
   valido solo per quel job, avvia i servizi;
5. attende che `https://<dominio>/api/health` risponda (fino a 5 minuti); se no, mostra i log dell'API.

La prima volta Keycloak e il certificato richiedono 1–2 minuti. Poi apri `https://<dominio>`.

## 11. Primo accesso e utenti

Come nella guida Oracle, [passo 7](deploy-oracle-cloud.md#7-primo-accesso-e-utenti): console su
`https://<dominio>/sso/admin` con `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`, amministratore
nominativo, poi gli utenti del realm `gestionale` con l'attributo `tenant_id`.

## 12. Dopo

| Cosa | Come |
|------|------|
| Nuova versione | merge su `main` → *Release immagini* verde → **Deploy** |
| Tornare indietro | **Deploy** con un commit precedente (40 caratteri) |
| Backup del database | [passo 10 della guida Oracle](deploy-oracle-cloud.md#10-backup-del-database) (uguale); conviene copiarli anche fuori dal VPS |
| Comandi utili (stato, log, riavvio) | [passo 11 della guida Oracle](deploy-oracle-cloud.md#11-comandi-utili-sulla-macchina) |
| Email delle notifiche | [passo 9 della guida Oracle](deploy-oracle-cloud.md#9-email-delle-notifiche-facoltativo) |
| Aggiornamenti di sicurezza del sistema | automatici (`unattended-upgrades`); riavvio quando serve: `[ -f /var/run/reboot-required ] && sudo reboot` |

## Problemi frequenti

| Sintomo | Causa e soluzione |
|---------|-------------------|
| Il pannello rifiuta la chiave SSH | Togli il commento finale (testo dopo la chiave); in alternativa usa una chiave RSA (`ssh-keygen -t rsa -b 4096`) |
| `Enter passphrase for key` | È la passphrase della **tua** chiave: è normale |
| Passphrase dimenticata | Crea una nuova `civilbeefy` (passo 1, rispondi `y` a *Overwrite*); come root (password o console web del fornitore) sostituiscila: `echo "<nuova .pub>" > /root/.ssh/authorized_keys` e lo stesso in `/home/ubuntu/.ssh/authorized_keys` (poi riaggiungi con `>>` la chiave del deploy) |
| `Saving key "\\civilbeefy" failed: Permission denied` | Nel comando manca `$env:USERPROFILE\.ssh\`: il file finirebbe in `C:\` |
| `Waiting for cache lock … unattended-upgr` | Aggiornamenti automatici in corso: aspetta |
| `ssh-keyscan`: *unsupported KEX method* | OpenSSH di Windows troppo vecchio: leggi l'impronta dalla macchina (passo 9) |
| `nslookup`: *Non-existent domain* | Prova `nslookup <dominio> 1.1.1.1`; se risponde, è il DNS della rete del PC |
| Deploy: *Docker non è installato* / *non può usare Docker* | Passo 6; dopo `usermod` serve un nuovo accesso |
| Deploy: *APP_DOMAIN … non è* / *ACME_EMAIL … non valido* | Correggi `/opt/civilbeefy/.env` (passo 7) o la variabile `APP_DOMAIN` dell'environment |
| Deploy: *Host key verification failed* | `OCI_SSH_KNOWN_HOSTS` non corrisponde: rigenera la riga (passo 9) |
| Deploy: *Permission denied (publickey)* | Manca la chiave pubblica del deploy sul server, o `OCI_SSH_PRIVATE_KEY` non è completo |
| Il sito non ha il certificato | Porte 80/443 chiuse (passo 5 e firewall del fornitore) o DNS non ancora aggiornato; log: `docker compose logs caddy` |
