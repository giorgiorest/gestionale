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

- **Rilascio** (ogni push su `main`) richiama **Installa in produzione** (`workflow_call`) per lo
  stesso commit come ultimo job. Quel job usa l'environment `production` con **Required
  reviewers**: GitHub lo mette in attesa finché un revisore non approva.
- **Concorrenza**: un solo job alla volta sull'environment (gruppo `production`, condiviso con
  Prepara server); un deploy in attesa viene sostituito dal più recente. Le immagini hanno un gruppo
  per immagine, così un deploy in attesa non blocca i rilasci successivi.
- Deploy usa gli **strumenti della versione corrente** (checkout del workflow) e la **configurazione
  del commit da installare** (checkout in `release/`): i ritorni indietro funzionano anche verso
  commit precedenti a modifiche dei workflow.
- **Prepara il server** (manuale, idempotente) configura un server Ubuntu via SSH con `sudo`:
  aggiornamenti, fuso orario, `ufw` (22, 80, 443), SSH solo con chiave (file `01-…` per vincere su
  `50-cloud-init.conf`), Docker, cartella e `.env` con password generate sulla macchina **solo se
  manca**, backup notturno del database. Resta manuale solo ciò che richiede il pannello del
  fornitore o l'accesso iniziale come root (utente `ubuntu` con le chiavi).
- Il collegamento SSH è un'azione composita condivisa (`.github/actions/ssh-server`) con verifica
  dell'impronta e un test di connessione con messaggio chiaro.
- **Verifica workflow e script**: actionlint e shellcheck sulle modifiche a workflow e script.

## Aggiornamento — approvazione nel repository privato (2026-11-24, superato)

I *Required reviewers* degli environment sono disponibili nei repository privati solo con GitHub
Enterprise (con Free, Pro e Team solo nei repository pubblici): nel repository di Civil Beefy la
sezione non c'è. Quindi:

- di norma **Release immagini non installa**: chiude con un job che scrive «Versione pronta» e il link
  al Deploy; **l'avvio manuale di Deploy è l'approvazione** (solo chi ha accesso in scrittura può farlo);
- `AUTO_DEPLOY = true` resta per quando il piano offrirà i revisori, e va attivata solo con loro;
- il resto (controlli della macchina, concorrenza, ritorno indietro) non cambia.

## Aggiornamento — approvazione con un commento nello stesso run (2026-11-24, superato)

Avviare Deploy a mano dopo ogni rilascio era scomodo. Il job **approvazione** di Release immagini
apre un'issue «Rilascio pronto» (notifica e email al proprietario), chiude quelle dei rilasci
precedenti e aspetta un commento `/deploy` o `/annulla` di chi ha accesso in scrittura
(`author_association` OWNER, MEMBER o COLLABORATOR; i bot sono ignorati). Con `/deploy` il job
`deploy` parte nello stesso run.

- L'attesa consuma minuti di Actions: al massimo `DEPLOY_APPROVAL_MINUTES` (default 30), poi la
  richiesta scade e resta il Deploy manuale. Un rilascio più recente annulla l'attesa precedente.
- Scartate: un'action di terze parti per l'approvazione (dipendenza in più con il token del
  repository) e un workflow su `issue_comment` (nessuna attesa, ma il deploy finisce in un run
  separato, meno leggibile).

## Aggiornamento — repository pubblico: tornano i Required reviewers (2026-11-24)

**Supera i due aggiornamenti qui sopra.** Il repository di Civil Beefy è **pubblico**, e sui
repository pubblici i *Required reviewers* degli environment ci sono con qualsiasi piano, Free
compreso. Il presupposto di quei due aggiornamenti — «la sezione non c'è» — era sbagliato.

Si torna quindi alla decisione originale, semplificata:

- **Rilascio** richiama **Installa in produzione** come ultimo job, senza condizioni: è
  l'environment `production` a tenerlo in attesa finché un revisore non approva.
- Rimossi il job `approvazione`, lo script `.github/scripts/attendi-approvazione.sh` e il
  meccanismo dell'issue «Rilascio pronto» con i commenti `/deploy` e `/annulla`.
- Rimossa la variabile **`AUTO_DEPLOY`**: esisteva solo per distinguere i piani con e senza
  revisori. Ora il blocco è sempre lo stesso.

Cosa si guadagna: niente script da mantenere, nessun minuto di Actions speso ad aspettare
(il job in attesa non consuma runner), nessuna scadenza a 30 minuti, e l'approvazione resta
registrata nella cronologia dei deployment di GitHub invece che in un'issue.

Cosa si perde: le richieste in attesa non si annullano più a vicenda. Dopo più merge ravvicinati
possono esserci più deployment in attesa; va approvato quello del commit più recente.

**Il blocco è una configurazione, non codice.** Se i Required reviewers venissero tolti
dall'environment `production`, ogni push su `main` installerebbe in produzione senza chiedere
nulla, e nessun file di questo repository se ne accorgerebbe.

### Nomi dei workflow

Nella stessa occasione i workflow hanno preso nomi che dicono cosa fanno, perché nella scheda
Actions si legge il nome, non il file: *Verifica API*, *Verifica web*, *Installer Windows*,
*APK Android*, *Verifica workflow e script*, *Rilascio*, *Installa in produzione*,
*Prepara il server*. I nomi dei file non cambiano: li usano `workflow_call`, i filtri `paths`
e i collegamenti nella documentazione.

## Conseguenze

- Ogni merge su `main` genera una richiesta di approvazione (email di GitHub). Chi non la approva
  lascia la produzione alla versione precedente; la richiesta scade dopo 30 giorni.
- Le password di produzione nascono sulla macchina e non passano mai da GitHub; per leggerle serve
  l'accesso SSH.
- Prepara server non è pensato per le immagini Oracle (regole `iptables` proprie): lì valgono la
  guida Oracle.

## Alternative scartate

- **Deploy completamente automatico**: nessun controllo umano prima di toccare la produzione.
- **Deploy avviato da `workflow_run`** al termine del Rilascio: due run separati, più
  difficile seguire quale commit si sta installando; `workflow_call` tiene tutto in un unico run.
- **Ansible** per preparare il server: uno strumento in più per pochi passi idempotenti, che uno
  script bash copre bene.
- **Creazione del VPS da API** (Aruba, OVH): API diverse per ogni fornitore e piani non sempre
  disponibili via API; si crea dal pannello una volta sola.
