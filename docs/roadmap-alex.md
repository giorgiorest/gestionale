# Roadmap "Alex" — funzionalità dalla versione del collega

Fonte: analisi dell'artifact **"CivilBeefy Beta"** di un collega (Claude, 24/11/2026). È un prototipo
a parte (single-page JS, non Angular/Spring Boot), utile come **specifica funzionale**, non come
codice da riusare: le funzionalità qui elencate vanno costruite secondo lo stack e il ricettario di
CLAUDE.md (migrazione Flyway con `add_sync_columns`/RLS, backend `Entity/Repository/Dto/Service/Controller`,
frontend con `SyncService`, tabella Dexie, permessi in `auth/Permission.java`).

Questo file **affianca** `docs/roadmap.md`, non lo sostituisce: resta la fonte di verità per il
processo di sviluppo (Fase 0), la sincronizzazione (Fase 1) e le anagrafiche di base (clienti,
fornitori, commesse, documenti/fatturazione). Le voci qui sotto **presuppongono** che esistano già:

> **Aggiornamento**: il nucleo rapportino/ore/assegnazione di questo file (ex A.2–A.4, A.7) è stato
> confrontato con un piano tecnico più solido ricevuto dall'utente ("Analisi_Completa.md", v0.1,
> 25/11/2026) e **superato** da esso: il modello di riferimento per quel nucleo sono ora le epiche
> GitHub [#40](https://github.com/giorgiorest/gestionale/issues/40)–[#45](https://github.com/giorgiorest/gestionale/issues/45)
> (R0–R5), non più le vecchie voci. Il resto di questo file (Fasi A estensioni, B–H) è stato a sua
> volta trasformato in epiche/storie GitHub (#61–#113), elencate nelle intestazioni di fase sotto.

- **Commesse/cantieri** (`docs/roadmap.md` 3.1): ogni voce "di cantiere" si aggancia a una commessa.
- **Fornitori** base (`docs/roadmap.md` 2.2): le voci della Fase E la estendono, non la duplicano.
- **Risorse e squadre** (`docs/roadmap.md` 2.3): le voci della Fase A la estendono con ruoli,
  presenze e scadenze specifiche del cantiere.

Una pull request = una voce di questa tabella, con lo stesso ricettario di avvio di `docs/roadmap.md`
(sessione nuova, prompt con numero voce e percorsi, piano prima del codice sulle voci grandi,
`/code-review` + `/simplify` in chiusura, aggiornamento dello stato nella stessa PR).

## Legenda

Stato: ⬜ da fare · 🟨 in corso · ✅ fatto · ⏸️ in attesa di una decisione esterna · ➡️ superata da
un'altra voce (non va più fatta come scritto qui, vedi testo della riga).
🔐 = passa da `/security-review` (dati riservati del personale, allegati, documenti finanziari,
integrazione con posta esterna). La colonna *Migr.* è sempre **da assegnare**: i numeri Flyway
V7 in su sono già indicati come "previsti" in `docs/roadmap.md`; il numero reale di ogni voce va
preso dal prossimo libero segnalato dall'hook `SessionStart` al momento di aprire la PR, non da
questa tabella (vedi [ADR 0013](decisioni/0013-numerazione-migrazioni.md)).

---

> **Nota su A.1**: non esiste un ruolo `MANAGER` a parte — il ruolo `admin` già esistente (tutte le
> funzioni) corrisponde al "manager o proprietà" del collega. Dove le voci sotto dicono "manager" o
> "amministrativo" si intende: `admin` per il primo, il nuovo ruolo `amministrativo` per il secondo
> (accesso limitato, mai a rapportini/contabilità/pianificazione).

## Fase A — Personale di cantiere: ruoli, ore, assenze e scadenze

Voci A.5 e successive in [Epica GitHub #61](https://github.com/giorgiorest/gestionale/issues/61).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| A.1 | Ruoli di cantiere (capocantiere, caposquadra, operaio, amministrativo) oltre ai ruoli già esistenti: nessun permesso proprio ancora (lo aggiunge ogni PR successiva che introduce la relativa funzione) | — | ✅ |
| A.2 | ➡️ **Superata** dal piano tecnico: GitHub [#47](https://github.com/giorgiorest/gestionale/issues/47) (Resource Assignment), [#49](https://github.com/giorgiorest/gestionale/issues/49) (Daily Report), [#50](https://github.com/giorgiorest/gestionale/issues/50) (Work Entry), [#51](https://github.com/giorgiorest/gestionale/issues/51) (Submit/Close) — modello più solido di «ore + lavorazione a testo libero» | — | ➡️ |
| A.3 | ➡️ **Confluita** in GitHub [#50](https://github.com/giorgiorest/gestionale/issues/50)/[#51](https://github.com/giorgiorest/gestionale/issues/51): il dettaglio UX (evidenza della differenza operaio↔capocantiere, vince il capocantiere) va integrato lì quando si lavorano quelle storie | — | ➡️ |
| A.4 | ➡️ **Confluita** in GitHub [#50](https://github.com/giorgiorest/gestionale/issues/50)/[#51](https://github.com/giorgiorest/gestionale/issues/51): il dettaglio UX (applicazione collettiva alla squadra) va integrato lì | — | ➡️ |
| A.5 | Cassa integrazione (CIG): campo separato dalle ore lavorate, per persona/squadra/giorno — [#69](https://github.com/giorgiorest/gestionale/issues/69) | da assegnare | ⬜ |
| A.6 | Ferie e malattia: richieste e relativa approvazione — [#70](https://github.com/giorgiorest/gestionale/issues/70) | da assegnare | ⬜ |
| A.7 | ➡️ **Superata** da GitHub [#47](https://github.com/giorgiorest/gestionale/issues/47) (Resource Assignment): periodi di validità e ruolo contestuale, più solido del semplice flag impegna/rilascia | — | ➡️ |
| A.8 | Subappalto e cottimo: ditte esterne con personale e mezzi associati al cantiere — [#71](https://github.com/giorgiorest/gestionale/issues/71) | da assegnare | ⬜ |
| A.9 | 🔐 Scheda anagrafica estesa del personale (contratto, IBAN, documenti e relative scadenze), visibile solo a manager/amministrativo — [#73](https://github.com/giorgiorest/gestionale/issues/73) | da assegnare | ⬜ |
| A.10 | 🔐 Corsi di formazione sicurezza: catalogo (RSPP, ASPP, preposto, PLE, gru, DPI, ecc.), scadenze calcolate, upload attestati — [#74](https://github.com/giorgiorest/gestionale/issues/74) | da assegnare | ⬜ |
| A.11 | 🔐 Lettura automatica AI degli attestati (foto/PDF → corso e data riconosciuti) — [#75](https://github.com/giorgiorest/gestionale/issues/75) | — | ⬜ |
| A.12 | Cruscotto scadenze personale (documenti + corsi) con filtri per orizzonte temporale (scadute/30/60/90 gg) — [#72](https://github.com/giorgiorest/gestionale/issues/72) | — | ⬜ |
| A.13 | 🔐 Falla pre-esistente notata in [ADR 0021](decisioni/0021-storico-costi-manodopera.md) (introdotta da #47/#51, non da #53): `hourlyCostOverride` (costo orario, contestuale a una commessa) esposto da `GET /api/commesse/{id}/resource-assignments` a chi ha `resource-assignments:read` ma non `labor-cost:read` (`caposquadra`, `operaio`) — chiusa in [PR #125](https://github.com/giorgiorest/gestionale/pull/125) nascondendo il campo nella risposta | — | ✅ |

## Fase B — Attrezzature e mezzi

[Epica GitHub #62](https://github.com/giorgiorest/gestionale/issues/62).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| B.1 | Anagrafica attrezzature/mezzi: inventario di cantiere, parco di proprietà — [#76](https://github.com/giorgiorest/gestionale/issues/76) | da assegnare | ⬜ |
| B.2 | Ore di utilizzo attrezzature per cantiere, con conferma del capocantiere — [#77](https://github.com/giorgiorest/gestionale/issues/77) | — | ⬜ |
| B.3 | Noleggi e acquisti di attrezzature — [#78](https://github.com/giorgiorest/gestionale/issues/78) | da assegnare | ⬜ |
| B.4 | Segnalazioni di guasti o problemi sulle attrezzature — [#79](https://github.com/giorgiorest/gestionale/issues/79) | da assegnare | ⬜ |
| B.5 | Scadenze attrezzature: manutenzione, revisione, assicurazione, verifica periodica INAIL, fine noleggio — [#80](https://github.com/giorgiorest/gestionale/issues/80) | — | ⬜ |
| B.6 | Vendita di un'attrezzatura (uscita dal parco, storico e rapportini mantenuti) — [#81](https://github.com/giorgiorest/gestionale/issues/81) | — | ⬜ |
| B.7 | Fatture di manutenzione mezzi, caricate dall'amministrazione — [#82](https://github.com/giorgiorest/gestionale/issues/82) | da assegnare | ⬜ |
| B.8 | Registro gasolio: cisterne (cantiere/azienda), carichi e travasi, rifornimenti mezzi, avviso scorta sotto il 20% — [#83](https://github.com/giorgiorest/gestionale/issues/83) | da assegnare | ⬜ |
| B.9 | Consumo medio per mezzo (L/h o L/100 km) calcolato dai contachilometri/contaore — [#84](https://github.com/giorgiorest/gestionale/issues/84) | — | ⬜ |

## Fase C — Materiali

[Epica GitHub #63](https://github.com/giorgiorest/gestionale/issues/63).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| C.1 | Elenco materiali di cantiere — [#85](https://github.com/giorgiorest/gestionale/issues/85) | da assegnare | ⬜ |
| C.2 | 🔐 DDT: caricamento e archiviazione, materiale usato per cantiere/giorno — [#86](https://github.com/giorgiorest/gestionale/issues/86) | da assegnare | ⬜ |
| C.3 | 🔐 Lettura automatica AI dei DDT (foto/PDF → righe materiale estratte) — [#87](https://github.com/giorgiorest/gestionale/issues/87) | — | ⬜ |

## Fase D — Compiti e rapportino di cantiere

[Epica GitHub #64](https://github.com/giorgiorest/gestionale/issues/64). Nessun equivalente nel
piano tecnico: è un'aggiunta, non una sovrapposizione (D.4 condivide solo la UI con #49–#51).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| D.1 | Lista compiti a cascata: manager → capocantiere → squadre/operai — [#88](https://github.com/giorgiorest/gestionale/issues/88) | da assegnare | ⬜ |
| D.2 | Compito con destinatari, attrezzature necessarie, posizione (mappa/GPS), foto — [#89](https://github.com/giorgiorest/gestionale/issues/89) | — | ⬜ |
| D.3 | Esito per persona (fatto/non finito, nota, foto di riscontro) — [#90](https://github.com/giorgiorest/gestionale/issues/90) | — | ⬜ |
| D.4 | Rapportino del giorno con archivio — [#91](https://github.com/giorgiorest/gestionale/issues/91) | da assegnare | ⬜ |
| D.5 | Dettatura vocale per l'inserimento rapido del rapportino (offline registra, online trascrive) — [#92](https://github.com/giorgiorest/gestionale/issues/92) — **decisione aperta**, vedi tabella sotto | — | ⬜ |

## Fase E — Fornitori avanzati

[Epica GitHub #65](https://github.com/giorgiorest/gestionale/issues/65). E.2/E.5/E.6: AI sui
documenti, **decisione aperta** (vedi tabella sotto).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| E.1 | Anagrafica unica fornitori/subappaltatori/noleggiatori, auto-rilevata dagli altri moduli — [#93](https://github.com/giorgiorest/gestionale/issues/93) | — | ⬜ |
| E.2 | 🔐 Compilazione automatica scheda fornitore via AI da documento (visura, fattura, biglietto da visita, testo web), con validazione P.IVA — [#94](https://github.com/giorgiorest/gestionale/issues/94) | — | ⬜ |
| E.3 | Indagine di mercato — [#95](https://github.com/giorgiorest/gestionale/issues/95) | da assegnare | ⬜ |
| E.4 | Richieste di offerta (RDO) con codice univoco e documenti allegati — [#96](https://github.com/giorgiorest/gestionale/issues/96) | da assegnare | ⬜ |
| E.5 | 🔐 Integrazione Gmail: lettura automatica delle risposte dei fornitori, riconoscimento degli inoltri — [#97](https://github.com/giorgiorest/gestionale/issues/97) | — | ⬜ |
| E.6 | 🔐 Estrazione AI dalla mail del fornitore (tipo risposta, importo, consegna, validità, note) — [#98](https://github.com/giorgiorest/gestionale/issues/98) | — | ⬜ |

## Fase F — Contabilità di cantiere

[Epica GitHub #66](https://github.com/giorgiorest/gestionale/issues/66). Dopo l'incremento 12 del
piano tecnico ("WBS e budget", [#58](https://github.com/giorgiorest/gestionale/issues/58)): terreno
condiviso sui costi, non duplicato. F.5: AI sui documenti, **decisione aperta** (vedi tabella sotto).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| F.1 | Costi industriali — [#99](https://github.com/giorgiorest/gestionale/issues/99) | da assegnare | ⬜ |
| F.2 | Contabilità attiva — [#100](https://github.com/giorgiorest/gestionale/issues/100) | da assegnare | ⬜ |
| F.3 | Contabilità passiva — [#101](https://github.com/giorgiorest/gestionale/issues/101) | da assegnare | ⬜ |
| F.4 | 🔐 SAL dei subappaltatori: stato da confermare/confermato con blocco, log eventi, allegati — [#102](https://github.com/giorgiorest/gestionale/issues/102) | da assegnare | ⬜ |
| F.5 | 🔐 Lettura automatica AI di SAL e registro di contabilità (PDF/Excel/Word/foto) — [#103](https://github.com/giorgiorest/gestionale/issues/103) | — | ⬜ |
| F.6 | Vista SAL per ditta subappaltatrice — [#104](https://github.com/giorgiorest/gestionale/issues/104) | — | ⬜ |

## Fase G — Pianificazione (Gantt/WBS)

[Epica GitHub #67](https://github.com/giorgiorest/gestionale/issues/67). Complementare (tempo),
non sovrapposta, all'incremento 12 del piano tecnico ("WBS e budget", costo,
[#58](https://github.com/giorgiorest/gestionale/issues/58)): condividono `WorkActivity.parentId`
(incremento 2, [#48](https://github.com/giorgiorest/gestionale/issues/48)). G.6: AI sui documenti,
**decisione aperta** (vedi tabella sotto).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| G.1 | Gantt: attività e scheda attività, collegate alla commessa — [#105](https://github.com/giorgiorest/gestionale/issues/105) | da assegnare | ⬜ |
| G.2 | WBS con costi — [#106](https://github.com/giorgiorest/gestionale/issues/106) | da assegnare | ⬜ |
| G.3 | Confronto pianificato vs reale — [#107](https://github.com/giorgiorest/gestionale/issues/107) | — | ⬜ |
| G.4 | Importazione cronoprogramma da Excel (riconoscimento intelligente delle colonne) — [#108](https://github.com/giorgiorest/gestionale/issues/108) | — | ⬜ |
| G.5 | Importazione cronoprogramma da XML MS Project — [#109](https://github.com/giorgiorest/gestionale/issues/109) | — | ⬜ |
| G.6 | Importazione cronoprogramma via AI da PDF/immagine (con stima delle date se non esplicite) — [#110](https://github.com/giorgiorest/gestionale/issues/110) | — | ⬜ |

## Fase H — Archivio documentale di cantiere

[Epica GitHub #68](https://github.com/giorgiorest/gestionale/issues/68). Complementare
all'incremento 13 del piano tecnico ("Documenti/scadenze",
[#59](https://github.com/giorgiorest/gestionale/issues/59)): quell'entità `Documento` generica può
essere il modello sotto queste tre viste specifiche.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| H.1 | Foto di cantiere organizzate per settimana — [#111](https://github.com/giorgiorest/gestionale/issues/111) | da assegnare | ⬜ |
| H.2 | 🔐 Contrattualistica — [#112](https://github.com/giorgiorest/gestionale/issues/112) | da assegnare | ⬜ |
| H.3 | Stazione appaltante — [#113](https://github.com/giorgiorest/gestionale/issues/113) | — | ⬜ |

---

## Decisioni ancora da prendere

| Cosa | Perché conta | Blocca |
|------|---------------|--------|
| Ruolo d'account (`admin`/`operator`/`viewer`) e ruolo di cantiere (`capocantiere`/`caposquadra`/`operaio`/`amministrativo`) sono oggi entrambi **a scelta singola** per utente (un solo `role` in Keycloak, un solo campo nella console di piattaforma), ma nella realtà il titolare di un'impresa piccola è spesso anche il capocantiere sul campo. Da decidere: accumulare i permessi di cantiere dentro `admin` quando serve, oppure passare a ruoli multipli per utente (cambio più grande: doc Keycloak, selettore multiplo in console, `PlatformDtos.role` da stringa a lista). | Prima si decide, meno codice delle fasi A–H si scrive sull'assunzione sbagliata | dalla PR che per prima richiede a un utente due ruoli insieme (probabile già in A.2/A.3) |
| **AI sui documenti**: il piano tecnico ricevuto dall'utente ("Analisi_Completa.md") esclude esplicitamente l'AI dallo scope iniziale ("OCR/AI su DDT/fatture e dettatura automatica del rapportino, fuori scope"); questo file invece la mette al centro di sei voci (E.2, E.5, E.6, C.3, A.11, F.5, G.6) come probabile differenziante di prodotto. Due filosofie diverse, non una questione tecnica. | Decide se l'AI sui documenti è un differenziante da costruire presto o un'estensione da rimandare a dopo le fondamenta | E.2, E.5, E.6, C.3, A.11, F.5, G.6 |
| **Dettatura vocale** (D.5): stessa esclusione esplicita nel piano tecnico, stesso bivio del punto sopra ma specifico alla dettatura. | Idem | D.5 |

## Note di sequenza

- Risorse e squadre (`docs/roadmap.md` 2.3) e Commesse/cantieri (3.1) sono **già costruite**: tutte
  le fasi sotto si appoggiano su di esse.
- Il nucleo rapportino/ore/assegnazione (ex A.2–A.4, A.7) è **superato** dal piano tecnico allegato
  ("Analisi_Completa.md"): seguire le epiche GitHub
  [#40](https://github.com/giorgiorest/gestionale/issues/40) (R0 — Foundation),
  [#41](https://github.com/giorgiorest/gestionale/issues/41) (R1 — Rapportino MVP),
  [#42](https://github.com/giorgiorest/gestionale/issues/42) (R2 — Controllo manodopera) come
  modello di riferimento, non le vecchie voci.
- Le Fasi B, C, D possono procedere in parallelo alla Fase A, ma D.4 (rapportino/archivio) condivide
  la UI del rapportino con le storie [#49](https://github.com/giorgiorest/gestionale/issues/49)–[#51](https://github.com/giorgiorest/gestionale/issues/51): tenerle vicine.
- La Fase E estende i fornitori di base (`docs/roadmap.md` 2.2, non ancora costruita): va fatta dopo.
- La Fase F (SAL, contabilità) ha senso dopo l'incremento 12 del piano tecnico ("WBS e budget",
  [#58](https://github.com/giorgiorest/gestionale/issues/58)) e dopo la fatturazione di base
  (`docs/roadmap.md` Fase 4).
- La Fase G (Gantt/WBS) è indipendente ma più utile una volta che esistono le commesse (già fatte)
  e i costi industriali (F.1).
