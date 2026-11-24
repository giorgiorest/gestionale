# Roadmap "Alex" — funzionalità dalla versione del collega

Fonte: analisi dell'artifact **"CivilBeefy Beta"** di un collega (Claude, 24/11/2026). È un prototipo
a parte (single-page JS, non Angular/Spring Boot), utile come **specifica funzionale**, non come
codice da riusare: le funzionalità qui elencate vanno costruite secondo lo stack e il ricettario di
CLAUDE.md (migrazione Flyway con `add_sync_columns`/RLS, backend `Entity/Repository/Dto/Service/Controller`,
frontend con `SyncService`, tabella Dexie, permessi in `auth/Permission.java`).

Questo file **affianca** `docs/roadmap.md`, non lo sostituisce: resta la fonte di verità per il
processo di sviluppo (Fase 0), la sincronizzazione (Fase 1) e le anagrafiche di base (clienti,
fornitori, commesse, documenti/fatturazione). Le voci qui sotto **presuppongono** che esistano già:

- **Commesse/cantieri** (`docs/roadmap.md` 3.1): ogni voce "di cantiere" si aggancia a una commessa.
- **Fornitori** base (`docs/roadmap.md` 2.2): le voci della Fase E la estendono, non la duplicano.
- **Risorse e squadre** (`docs/roadmap.md` 2.3): le voci della Fase A la estendono con ruoli,
  presenze e scadenze specifiche del cantiere.

Una pull request = una voce di questa tabella, con lo stesso ricettario di avvio di `docs/roadmap.md`
(sessione nuova, prompt con numero voce e percorsi, piano prima del codice sulle voci grandi,
`/code-review` + `/simplify` in chiusura, aggiornamento dello stato nella stessa PR).

## Legenda

Stato: ⬜ da fare · 🟨 in corso · ✅ fatto · ⏸️ in attesa di una decisione esterna.
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

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| A.1 | Ruoli di cantiere (capocantiere, caposquadra, operaio, amministrativo) oltre ai ruoli già esistenti: nessun permesso proprio ancora (lo aggiunge ogni PR successiva che introduce la relativa funzione) | — | ✅ |
| A.2 | Rapportino ore: inserimento giornaliero dell'operaio (ore + lavorazione svolta), invio al capocantiere | da assegnare | ⬜ |
| A.3 | Verifica e conferma ore da parte del capocantiere, con evidenza delle differenze operaio↔capocantiere (vince il capocantiere) | — | ⬜ |
| A.4 | Caposquadra: gestione ore e lavorazioni di tutta la squadra, con applicazione collettiva | — | ⬜ |
| A.5 | Cassa integrazione (CIG): campo separato dalle ore lavorate, per persona/squadra/giorno | da assegnare | ⬜ |
| A.6 | Ferie e malattia: richieste e relativa approvazione | da assegnare | ⬜ |
| A.7 | Gestione multi-cantiere del personale: "impegna/rilascia" una persona su un cantiere, disponibilità visibile sugli altri | da assegnare | ⬜ |
| A.8 | Subappalto e cottimo: ditte esterne con personale e mezzi associati al cantiere | da assegnare | ⬜ |
| A.9 | 🔐 Scheda anagrafica estesa del personale (contratto, IBAN, documenti e relative scadenze), visibile solo a manager/amministrativo | da assegnare | ⬜ |
| A.10 | 🔐 Corsi di formazione sicurezza: catalogo (RSPP, ASPP, preposto, PLE, gru, DPI, ecc.), scadenze calcolate, upload attestati | da assegnare | ⬜ |
| A.11 | 🔐 Lettura automatica AI degli attestati (foto/PDF → corso e data riconosciuti) | — | ⬜ |
| A.12 | Cruscotto scadenze personale (documenti + corsi) con filtri per orizzonte temporale (scadute/30/60/90 gg) | — | ⬜ |

## Fase B — Attrezzature e mezzi

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| B.1 | Anagrafica attrezzature/mezzi: inventario di cantiere, parco di proprietà | da assegnare | ⬜ |
| B.2 | Ore di utilizzo attrezzature per cantiere, con conferma del capocantiere | — | ⬜ |
| B.3 | Noleggi e acquisti di attrezzature | da assegnare | ⬜ |
| B.4 | Segnalazioni di guasti o problemi sulle attrezzature | da assegnare | ⬜ |
| B.5 | Scadenze attrezzature: manutenzione, revisione, assicurazione, verifica periodica INAIL, fine noleggio | — | ⬜ |
| B.6 | Vendita di un'attrezzatura (uscita dal parco, storico e rapportini mantenuti) | — | ⬜ |
| B.7 | Fatture di manutenzione mezzi, caricate dall'amministrazione | da assegnare | ⬜ |
| B.8 | Registro gasolio: cisterne (cantiere/azienda), carichi e travasi, rifornimenti mezzi, avviso scorta sotto il 20% | da assegnare | ⬜ |
| B.9 | Consumo medio per mezzo (L/h o L/100 km) calcolato dai contachilometri/contaore | — | ⬜ |

## Fase C — Materiali

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| C.1 | Elenco materiali di cantiere | da assegnare | ⬜ |
| C.2 | 🔐 DDT: caricamento e archiviazione, materiale usato per cantiere/giorno | da assegnare | ⬜ |
| C.3 | 🔐 Lettura automatica AI dei DDT (foto/PDF → righe materiale estratte) | — | ⬜ |

## Fase D — Compiti e rapportino di cantiere

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| D.1 | Lista compiti a cascata: manager → capocantiere → squadre/operai | da assegnare | ⬜ |
| D.2 | Compito con destinatari, attrezzature necessarie, posizione (mappa/GPS), foto | — | ⬜ |
| D.3 | Esito per persona (fatto/non finito, nota, foto di riscontro) | — | ⬜ |
| D.4 | Rapportino del giorno con archivio | da assegnare | ⬜ |
| D.5 | Dettatura vocale per l'inserimento rapido del rapportino (offline registra, online trascrive) | — | ⬜ |

## Fase E — Fornitori avanzati

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| E.1 | Anagrafica unica fornitori/subappaltatori/noleggiatori, auto-rilevata dagli altri moduli | — | ⬜ |
| E.2 | 🔐 Compilazione automatica scheda fornitore via AI da documento (visura, fattura, biglietto da visita, testo web), con validazione P.IVA | — | ⬜ |
| E.3 | Indagine di mercato | da assegnare | ⬜ |
| E.4 | Richieste di offerta (RDO) con codice univoco e documenti allegati | da assegnare | ⬜ |
| E.5 | 🔐 Integrazione Gmail: lettura automatica delle risposte dei fornitori, riconoscimento degli inoltri | — | ⬜ |
| E.6 | 🔐 Estrazione AI dalla mail del fornitore (tipo risposta, importo, consegna, validità, note) | — | ⬜ |

## Fase F — Contabilità di cantiere

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| F.1 | Costi industriali | da assegnare | ⬜ |
| F.2 | Contabilità attiva | da assegnare | ⬜ |
| F.3 | Contabilità passiva | da assegnare | ⬜ |
| F.4 | 🔐 SAL dei subappaltatori: stato da confermare/confermato con blocco, log eventi, allegati | da assegnare | ⬜ |
| F.5 | 🔐 Lettura automatica AI di SAL e registro di contabilità (PDF/Excel/Word/foto) | — | ⬜ |
| F.6 | Vista SAL per ditta subappaltatrice | — | ⬜ |

## Fase G — Pianificazione (Gantt/WBS)

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| G.1 | Gantt: attività e scheda attività, collegate alla commessa | da assegnare | ⬜ |
| G.2 | WBS con costi | da assegnare | ⬜ |
| G.3 | Confronto pianificato vs reale | — | ⬜ |
| G.4 | Importazione cronoprogramma da Excel (riconoscimento intelligente delle colonne) | — | ⬜ |
| G.5 | Importazione cronoprogramma da XML MS Project | — | ⬜ |
| G.6 | Importazione cronoprogramma via AI da PDF/immagine (con stima delle date se non esplicite) | — | ⬜ |

## Fase H — Archivio documentale di cantiere

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| H.1 | Foto di cantiere organizzate per settimana | da assegnare | ⬜ |
| H.2 | Contrattualistica | da assegnare | ⬜ |
| H.3 | Stazione appaltante | — | ⬜ |

---

## Decisioni ancora da prendere

| Cosa | Perché conta | Blocca |
|------|---------------|--------|
| Ruolo d'account (`admin`/`operator`/`viewer`) e ruolo di cantiere (`capocantiere`/`caposquadra`/`operaio`/`amministrativo`) sono oggi entrambi **a scelta singola** per utente (un solo `role` in Keycloak, un solo campo nella console di piattaforma), ma nella realtà il titolare di un'impresa piccola è spesso anche il capocantiere sul campo. Da decidere: accumulare i permessi di cantiere dentro `admin` quando serve, oppure passare a ruoli multipli per utente (cambio più grande: doc Keycloak, selettore multiplo in console, `PlatformDtos.role` da stringa a lista). | Prima si decide, meno codice delle fasi A–H si scrive sull'assunzione sbagliata | dalla PR che per prima richiede a un utente due ruoli insieme (probabile già in A.2/A.3) |

## Note di sequenza

- La Fase A dipende da "Risorse e squadre" (`docs/roadmap.md` 2.3): va fatta prima, se non già fatta.
- Le Fasi B, C, D possono procedere in parallelo ad A una volta pronte le commesse (3.1 della roadmap
  principale), ma D (compiti/rapportino) condivide la UI del rapportino con A.2–A.4: tenerle vicine.
- La Fase E estende i fornitori di base (`docs/roadmap.md` 2.2): va fatta dopo.
- La Fase F (SAL, contabilità) ha senso dopo la Fase E (fornitori/subappaltatori) e dopo la
  fatturazione di base (`docs/roadmap.md` Fase 4).
- La Fase G (Gantt/WBS) è indipendente ma più utile una volta che esistono le commesse e i costi
  industriali (F.1).
