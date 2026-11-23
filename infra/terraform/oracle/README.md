# Infrastruttura Oracle Cloud con Terraform

Crea da GitHub, senza passare dalla console, tutto ciò che serve a civil beefy su Oracle Cloud
(piano Always Free):

| Risorsa | Dettaglio |
|---------|-----------|
| Rete (VCN) e subnet pubblica | `civilbeefy-vcn`, `10.0.0.0/16` e `10.0.0.0/24` |
| Firewall di Oracle (security list) | aperte solo 22 (SSH), 80, 443 TCP e 443 UDP |
| Macchina | Ampere A1 (arm64), Ubuntu 24.04, 2 OCPU e 12 GB, disco da 100 GB |
| IP pubblico **riservato** | non cambia anche se la macchina viene ricreata |
| Primo avvio (cloud-init) | porte 80/443 nel firewall della macchina, Docker, cartella `/opt/civilbeefy` |

Il file `.env` con le password resta un passo manuale (passo 4 di
[docs/deploy-oracle-cloud.md](../../../docs/deploy-oracle-cloud.md)): i segreti di produzione non
passano da Terraform né dal suo stato.

## I workflow

| Workflow | Quando | Cosa fa |
|----------|--------|---------|
| **Infrastruttura Oracle** (`infra.yml`) | a mano | `plan` mostra cosa cambierebbe; `apply` lo applica |
| **Infrastruttura Oracle (tentativi)** (`infra-retry.yml`) | ogni ora, se `OCI_RETRY_ENABLED=true` | riprova `apply` finché la macchina esiste, poi si disattiva da solo |
| **CI Infrastruttura** (`ci-infra.yml`) | sulle pull request | controlla formato e validità dei file, senza credenziali |

Le macchine A1 gratuite spesso non sono disponibili («Out of capacity»). Per questo:

- se mancano 2 OCPU e 12 GB, il workflow riprova subito con **1 OCPU e 6 GB** (bastano per iniziare;
  si ingrandisce poi cambiando `OCI_OCPUS` e `OCI_MEMORY_GB` e lanciando *Infrastruttura Oracle → apply*);
- la mancanza di capacità non è un errore: il run finisce con un avviso e il tentativo successivo
  riparte da dove era rimasto (la rete resta creata).

**Protezione**: senza l'opzione `allow_destroy` (solo nel lancio manuale) un piano che elimina o
ricrea risorse viene fermato. Fa eccezione solo una macchina rimasta a metà da un tentativo fallito.

## Configurazione (una volta sola)

### 1. Eliminare la rete creata a mano (se c'è)

Se avevi creato `civilbeefy-vcn` dalla console, eliminala per non averne due:
☰ → **Networking → Virtual cloud networks** → `civilbeefy-vcn` → **Delete** (elimina anche subnet e
gateway). Terraform la ricrea con le porte già aperte.

### 2. Bucket per lo stato di Terraform

Terraform ricorda cosa ha creato in un file di stato, salvato su Object Storage (gratuito fino a 20 GB).

1. ☰ → **Storage → Buckets** → compartment `(root)` → **Create bucket**.
2. Nome: `civilbeefy-tfstate`; *Default storage tier*: **Standard**; attiva **Enable object versioning**
   (permette di recuperare uno stato precedente). **Create**.
3. Annota il **Namespace** mostrato nella pagina del bucket (una sigla di lettere e numeri).

### 3. Chiave API di Oracle

1. In alto a destra: icona del profilo → **User settings** (o *My profile*) → **Tokens and keys**
   (o *API keys*) → **Add API key**.
2. **Generate API key pair** → **Download private key** (file `.pem`) → **Add**.
3. Compare un riquadro *Configuration file preview*: copia i valori di `user`, `fingerprint`,
   `tenancy` e `region`.

La chiave API ha gli stessi poteri del tuo utente sull'account: conservala solo nei GitHub Secrets
e cancella il file `.pem` dal PC quando hai finito.

### 4. Chiavi SSH

Sul tuo PC (PowerShell), una chiave per te e una per il workflow Deploy. Alla richiesta della
passphrase premi Invio due volte (per la chiave del deploy deve restare vuota):

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\civilbeefy" -C "accesso personale"
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\civilbeefy-deploy" -C "github-deploy"
Get-Content "$env:USERPROFILE\.ssh\civilbeefy.pub", "$env:USERPROFILE\.ssh\civilbeefy-deploy.pub"
```

L'ultimo comando stampa le due chiavi **pubbliche** (due righe che iniziano con `ssh-ed25519`).

### 5. Environment GitHub `oracle-cloud`

Repository → **Settings → Environments → New environment** → `oracle-cloud`.

- **Deployment branches and tags**: *Selected branches* → `main` (le credenziali le usa solo `main`).
- Non attivare *Required reviewers*: bloccherebbe i tentativi automatici.

| Tipo | Nome | Valore |
|------|------|--------|
| Secret | `OCI_TENANCY_OCID` | `tenancy` del passo 3 (`ocid1.tenancy.oc1..…`) |
| Secret | `OCI_USER_OCID` | `user` del passo 3 (`ocid1.user.oc1..…`) |
| Secret | `OCI_FINGERPRINT` | `fingerprint` del passo 3 |
| Secret | `OCI_PRIVATE_KEY` | contenuto **intero** del file `.pem` (righe BEGIN/END comprese) |
| Variable | `OCI_REGION` | `region` del passo 3, es. `eu-turin-1` |
| Variable | `OCI_NAMESPACE` | namespace del passo 2 |
| Variable | `OCI_SSH_PUBLIC_KEYS` | le due chiavi pubbliche del passo 4, una per riga |
| Variable | `OCI_STATE_BUCKET` | facoltativa, default `civilbeefy-tfstate` |
| Variable | `OCI_OCPUS`, `OCI_MEMORY_GB` | facoltative, default `2` e `12` |

### 6. Avvio

1. **Actions → Infrastruttura Oracle → Run workflow**, azione `plan`: verifica che le credenziali funzionino
   e mostra le risorse che verranno create (nessuna modifica).
2. Per i tentativi automatici: **Settings → Secrets and variables → Actions → Variables** (del
   *repository*, non dell'environment) → `OCI_RETRY_ENABLED` = `true`.
   In alternativa **Infrastruttura Oracle → Run workflow** con `apply` per un singolo tentativo.
3. Quando la macchina è creata, il riepilogo del run riporta **IP pubblico** e **impronta SSH**, e i
   tentativi si disattivano da soli.

Consumo: ogni tentativo usa circa 1 minuto di GitHub Actions (il piano gratuito per i repository
privati ne include 2000 al mese); i tentativi si fermano appena la macchina esiste. Per cambiare la
frequenza modifica `cron` in `.github/workflows/infra-retry.yml`.

### 7. Dopo la creazione

Continua da [docs/deploy-oracle-cloud.md](../../../docs/deploy-oracle-cloud.md):

- **passo 3**: DuckDNS o dominio verso l'IP pubblico;
- **passo 4**: solo il file `.env` (Docker, porte e cartella li ha già preparati il primo avvio; attendi
  5 minuti dalla creazione). Per collegarti:
  `ssh -i "$env:USERPROFILE\.ssh\civilbeefy" ubuntu@<IP>`;
- **passo 5**: environment `production` con `OCI_SSH_HOST` = IP, `OCI_SSH_USER` = `ubuntu`,
  `OCI_SSH_PRIVATE_KEY` = contenuto di `civilbeefy-deploy`, `OCI_SSH_KNOWN_HOSTS` = riga del riepilogo
  (la chiave pubblica del deploy è già sulla macchina);
- **passo 6**: primo rilascio.

## Modifiche successive

- Cambia i file `.tf` con una pull request (la CI li valida), poi dopo la merge
  **Infrastruttura Oracle → plan** e, se il piano è quello atteso, **apply**.
- Ingrandire la macchina (es. da 1 a 2 OCPU): aggiorna `OCI_OCPUS` / `OCI_MEMORY_GB` e lancia
  `apply`. La macchina si riavvia, i dati restano.
- Una nuova immagine Ubuntu o un `cloud-init.yaml` modificato valgono solo per una macchina nuova:
  non ricreano quella esistente.
- Ricreare o eliminare risorse richiede `allow_destroy`. Il database sta sul disco della macchina:
  prima fai un backup (passo 10 della guida).

## In locale (facoltativo)

Serve Terraform ≥ 1.12. Con le stesse credenziali in un file `backend.hcl` (vedi il workflow) e le
variabili `TF_VAR_…`:

```bash
terraform init -backend-config=backend.hcl
terraform plan
```

Non committare `backend.hcl`, `*.tfvars`, la chiave `.pem` o la cartella `.terraform/`.
