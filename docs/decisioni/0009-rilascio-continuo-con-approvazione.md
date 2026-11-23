# ADR 0009 — Rilascio continuo con approvazione e preparazione del server

- Stato: accettata
- Data: 2026-11-23

## Contesto

Fino a qui il deploy si avviava solo a mano (ADR 0002) e il server si preparava seguendo una guida
di comandi da copiare. Con il passaggio a un VPS (Aruba) servono:

- a ogni rilascio, che parta **tutto** (build, immagini, installer desktop) e alla fine il deploy,
  **approvato da una persona**;
- una pipeline di infrastruttura utile anche fuori da Oracle Cloud: la creazione della macchina dai
  pannelli dei VPS non è automatizzabile in modo uniforme, la sua configurazione sì.

## Decisione

- **Release immagini** (ogni push su `main`) richiama **Deploy** (`workflow_call`) per lo stesso
  commit come ultimo job. Il job di Deploy usa l'environment `production` con **Required
  reviewers**: GitHub lo mette in attesa finché un revisore non approva.
- Il collegamento automatico è attivo solo con la variabile di repository **`AUTO_DEPLOY = true`**,
  da impostare dopo aver configurato i revisori: senza revisori il deploy partirebbe senza conferma.
- **Concorrenza**: un solo job alla volta sull'environment (gruppo `production`, condiviso con
  Prepara server); un deploy in attesa viene sostituito dal più recente. Le immagini hanno un gruppo
  per immagine, così un deploy in attesa non blocca i rilasci successivi.
- Deploy usa gli **strumenti della versione corrente** (checkout del workflow) e la **configurazione
  del commit da installare** (checkout in `release/`): i ritorni indietro funzionano anche verso
  commit precedenti a modifiche dei workflow.
- **Prepara server** (manuale, idempotente) configura un server Ubuntu via SSH con `sudo`:
  aggiornamenti, fuso orario, `ufw` (22, 80, 443), SSH solo con chiave (file `01-…` per vincere su
  `50-cloud-init.conf`), Docker, cartella e `.env` con password generate sulla macchina **solo se
  manca**, backup notturno del database. Resta manuale solo ciò che richiede il pannello del
  fornitore o l'accesso iniziale come root (utente `ubuntu` con le chiavi).
- Il collegamento SSH è un'azione composita condivisa (`.github/actions/ssh-server`) con verifica
  dell'impronta e un test di connessione con messaggio chiaro.
- **CI Workflow**: actionlint e shellcheck sulle modifiche a workflow e script.

## Conseguenze

- Ogni merge su `main` genera una richiesta di approvazione (email di GitHub). Chi non la approva
  lascia la produzione alla versione precedente; la richiesta scade dopo 30 giorni.
- Le password di produzione nascono sulla macchina e non passano mai da GitHub; per leggerle serve
  l'accesso SSH.
- Prepara server non è pensato per le immagini Oracle (regole `iptables` proprie): lì valgono la
  guida Oracle e Terraform (ADR 0007).

## Alternative scartate

- **Deploy completamente automatico**: nessun controllo umano prima di toccare la produzione.
- **Deploy avviato da `workflow_run`** al termine di Release immagini: due run separati, più
  difficile seguire quale commit si sta installando; `workflow_call` tiene tutto in un unico run.
- **Ansible** per preparare il server: uno strumento in più per pochi passi idempotenti, che uno
  script bash copre bene.
- **Creazione del VPS da API** (Aruba, OVH): API diverse per ogni fornitore e piani non sempre
  disponibili via API; si crea dal pannello una volta sola.
