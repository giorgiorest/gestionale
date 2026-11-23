# ADR 0007 — Infrastruttura Oracle Cloud come codice (Terraform)

- Stato: accettata
- Data: 2026-11-23

## Contesto

La produzione gira su una macchina Oracle Cloud Always Free ([ADR 0002](0002-deploy-target.md)).
Crearla dalla console richiede molti passaggi manuali (rete, porte, IP, Docker) e le macchine
Ampere A1 gratuite sono spesso esaurite («Out of capacity»): bisogna riprovare per ore o giorni.
Il prodotto verrà anche installato per altri clienti, su altri account.

## Decisione

- **Terraform** (provider `oracle/oci`) in `infra/terraform/oracle` descrive rete, security list,
  macchina A1 con Ubuntu 24.04, IP pubblico riservato e il primo avvio (cloud-init: porte, Docker,
  cartella dell'app).
- **Stato** su Oracle Object Storage (backend `oci`), bucket creato a mano una volta, con versioni.
- **Workflow**:
  - `infra.yml`, manuale: `plan` o `apply`, environment `oracle-cloud` limitato a `main`;
  - `infra-retry.yml`, ogni ora se `OCI_RETRY_ENABLED=true`: riprova finché la macchina esiste,
    con ripiego su 1 OCPU e 6 GB, poi si disattiva;
  - `ci-infra.yml`: formato e validità sulle pull request, senza credenziali.
- **Nessuna eliminazione automatica**: un piano che elimina o ricrea risorse si ferma, salvo
  l'opzione esplicita `allow_destroy` nel lancio manuale.
- **Segreti di produzione fuori da Terraform**: il `.env` dell'applicazione resta sulla macchina;
  la chiave API di Oracle sta solo nei GitHub Secrets dell'environment.
- Modifiche a immagine o cloud-init non ricreano la macchina esistente (`ignore_changes`).

## Conseguenze

- Una nuova installazione (anche per un cliente) si crea con gli stessi file e gli stessi workflow.
- La chiave API ha i poteri dell'utente Oracle che l'ha generata. Miglioramento futuro: un utente
  dedicato con una policy limitata a un compartment.
- Il lock file dei provider (`.terraform.lock.hcl`) non è versionato finché non viene generato per
  tutte le piattaforme (`terraform providers lock`); la versione del provider è fissata a `~> 9.14`.
- La creazione manuale dalla console resta possibile (guida, passi 1–2): le due strade sono
  alternative, non da mescolare sulla stessa rete.

## Alternative scartate

- **Script con OCI CLI**: più semplice per un solo tentativo, ma senza stato né piano delle modifiche.
- **OCI Resource Manager** (Terraform gestito da Oracle): gratuito, ma lega la pipeline alla console
  Oracle e non si integra con le pull request.
- **Tentativi ogni pochi minuti**: più rapidi a trovare capacità, ma consumano troppi minuti di
  GitHub Actions su un repository privato.
