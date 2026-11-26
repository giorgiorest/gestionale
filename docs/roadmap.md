# Roadmap di Civil Beefy

Verticale di prodotto: **imprese edili e di cantiere** ([ADR 0012](decisioni/0012-verticale-edilizia.md)).

Questo file è la **fonte di verità** del lavoro da fare: non va tenuto in chat né in una issue. Una
pull request = una voce di questa tabella. Se una voce non sta in una sessione di lavoro, va spezzata
qui prima di cominciare.

Il file usa due numerazioni di voce, storicamente separate e qui riunite in un solo documento:
**Fasi 0–7** (numeriche: processo, sincronizzazione, anagrafiche di base, cuore rapportini,
fatturazione, robustezza, SaaS, assistente AI) e **Fasi A–I** (lettere: estensioni specifiche del
cantiere — personale, attrezzature, materiali, compiti, fornitori avanzati, contabilità,
pianificazione, archivio documentale, vicende contrattuali — nate dall'analisi di un prototipo di
un collega, **"CivilBeefy Beta"** di Claude del 24/11/2026: un prototipo a parte, single-page JS
non Angular/Spring Boot, usato come **specifica funzionale**, non come codice da riusare. Le voci
delle Fasi A–I vanno costruite secondo lo stack e il ricettario di CLAUDE.md, come tutte le altre.
Le Fasi A–I **presuppongono** che esistano già Commesse/cantieri (3.1), Risorse e squadre (2.3) e,
dove indicato, Fornitori (2.2); condividono con le Fasi 0–7 la stessa sequenza di migrazioni Flyway
e lo stesso ricettario di apertura PR.

[docs/ordine-esecuzione.md](ordine-esecuzione.md) incrocia tutte le Fasi di questo file per un
ordine di lavoro consigliato (onde parallelizzabili, dipendenze tra fasi): non è una fonte di
verità sullo stato, solo un suggerimento di sequenza.

## Come avviare una sessione di lavoro

1. Una **sessione nuova per ogni pull request**; non si riusa quella di una PR già unita.
2. Prompt di partenza con il numero della voce e i percorsi, non «trova dove sta»:

   > PR 2.1 della roadmap. Segui il ricettario in CLAUDE.md.
   > File: `apps/api/src/main/java/it/gestionale/api/customers/`, nuova migrazione,
   > `apps/web/src/app/features/customers/`.

3. Sulle voci grandi: **prima il piano**, poi il codice.
4. Chiusura: `/code-review` (livello `medium`; `high` o più solo su RLS, permessi, allegati,
   fatturazione e pagamenti), poi `/simplify`; `/security-review` sulle voci marcate 🔐.
5. Aggiorna lo **stato** della voce in questo file nella stessa PR.

## Legenda

Stato: ⬜ da fare · 🟨 in corso · ✅ fatto · ⏸️ in attesa di una decisione esterna · ➡️ superata da
un'altra voce (non va più fatta come scritto qui, vedi testo della riga).

🔐 = passa da `/security-review` (RLS nuova, allegati, documenti finanziari, dati riservati del
personale, integrazione con posta esterna). La colonna *Migr.* è il numero Flyway **previsto**:
per le Fasi 0–7 è un promemoria contro le collisioni, non una prenotazione; per le Fasi A–I è
quasi sempre «da assegnare» — il numero reale va preso dal prossimo libero segnalato dall'hook
`SessionStart` al momento di aprire la PR, non da questa tabella
([ADR 0013](decisioni/0013-numerazione-migrazioni.md)). Verifica sempre il numero più alto
applicato su `main`: i numeri qui sotto possono essere già presi da una voce dell'altra numerazione
prima che questa venga lavorata (es. V9–V11 sono già di `resource_assignments`, `work_activities`,
`daily_reports`).

---

## Fase 0 — Processo di sviluppo

Si ripaga entro la seconda PR: serve a non ri-scoprire il progetto a ogni sessione.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 0.1 | Ricettario in CLAUDE.md, questa roadmap, ADR 0012–0017, permessi di progetto, controllo delle migrazioni in CI | — | 🟨 |
| 0.2 | Catena di fornitura: Dependabot, `npm audit` e controllo dipendenze nella CI, scansione delle immagini, elenco delle licenze delle dipendenze | — | ✅ |
| 0.3 | ESLint e Prettier nella CI web, budget sulla dimensione del bundle | — | ⬜ |
| 0.4 | Osservabilità: log strutturati JSON con `tenant_id` e id della richiesta, metriche Actuator/Prometheus, avviso quando la salute cade | — | ✅ |
| 0.5 | **Backup fuori sito** (l'archivio oggi resta sulla stessa macchina) e **prova di ripristino** documentata | — | ⬜ |
| 0.6 | Hook `SessionStart`: branch, PR aperta, prossima voce di roadmap, primo numero Flyway libero | — | ✅ |

> La 0.5 è il rischio singolo più grande del progetto: un backup mai ripristinato non è un backup.

## Fase 1 — Fondamenta della sincronizzazione

**Da fare prima di aggiungere entità nuove**: ogni entità aggiunta senza sincronizzazione vera è
debito da pagare moltiplicato per il numero di entità.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 1.1 | Cancellazione logica (`deleted_at`), `DELETE /api/customers/{id}` idempotente, `@Version` per il blocco ottimistico, `created_by`/`updated_by` ([ADR 0018](decisioni/0018-cancellazione-logica.md)) | V6 | ✅ |
| 1.2 | `GET /api/sync/changes` con cursore per entità (`updated_at` + id) e tombstones | V17 | ✅ |
| 1.3 | `pullChanges` nel client: cursore in `syncState`, applicazione locale, schermata «modifiche non inviate» | — | ✅ |
| 1.4 | Liste paginate, ordinate e ricercabili ([ADR 0014](decisioni/0014-liste-paginate.md)) su `customers`, con gli indici | V18 | ✅ |
| 1.5 | Conflitti: `If-Match`/409, risoluzione nell'interfaccia | — | ⬜ |

## Fase 2 — Anagrafiche del mestiere

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 2.1 | Anagrafica clienti completa: ragione sociale, P.IVA, codice fiscale, indirizzo, PEC, codice destinatario SDI, condizioni di pagamento | V19 | ✅ |
| 2.2 | Fornitori: tabella e schema propri, non condivisi con `customers` (stessi campi del mestiere di 2.1: ragione sociale, dati fiscali, PEC/SDI, condizioni di pagamento — un fornitore non è un cliente con un'etichetta diversa, e le estensioni della Fase E non devono toccare l'anagrafica clienti) | V20 | ✅ |
| 2.3 | Risorse e squadre (dipendenti che compilano i rapportini), collegate alla rubrica `app_users` | V7 | ✅ |
| 2.4 | 🔐 Allegati lato backend: tabella con RLS, archivio compatibile S3, URL firmati, MinIO in sviluppo ([ADR 0016](decisioni/0016-archivio-allegati.md)) | da verificare | ⬜ |
| 2.5 | Allegati lato client: caricamento offline, foto dal telefono, cache selettiva, svuotamento all'uscita | — | ⬜ |

## Fase 3 — Il cuore: commesse e rapportini

È il differenziante del prodotto: funziona in cantiere, senza campo, dal telefono.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 3.1 | Commesse/cantieri: modello, stati, anagrafica, collegamento al cliente | V8 | ✅ |
| 3.2 | Rapportino giornaliero lato backend: ore per persona, materiali, note, allegati — testata ([#49](https://github.com/giorgiorest/gestionale/issues/49), `DailyReport`), ore ([#50](https://github.com/giorgiorest/gestionale/issues/50), `WorkEntry`) e materiali (`DailyReportMaterial`, testo libero: nessun catalogo materiali ancora, GitHub #55) fatti; **allegati rimandati a dopo la voce 2.4** (archivio generico S3/MinIO, ADR 0016), per non costruire due volte l'infrastruttura di upload | V16 | 🟨 |
| 3.3 | Rapportino sul telefono: interfaccia per schermo piccolo, compilazione offline, foto | — | ⬜ |
| 3.4 | Firma grafometrica sul rapportino (canvas, immagine allegata, impronta e orario) | — | ⬜ |
| 3.5 | Riepiloghi: ore per commessa, per persona, per periodo; export | — | ⬜ |
| 3.6 | Pianificazione squadre: calendario settimanale «chi va dove», sincronizzato sul telefono | da verificare | ⬜ |

## Fase 4 — Documenti e fatturazione

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 4.1 | Articoli e voci di listino | V15 | ⬜ |
| 4.2 | Documento: testata e righe, numerazione per anno e tipo, totali e IVA | V16 | ⬜ |
| 4.3 | Stati e transizioni: preventivo → ordine/commessa → DDT → fattura | — | ⬜ |
| 4.4 | Interfaccia del documento (lista, editor delle righe, offline) | — | ⬜ |
| 4.5 | PDF del documento con il marchio dell'installazione ([ADR 0015](decisioni/0015-generazione-pdf.md)) | — | ⬜ |
| 4.6 | Export CSV/Excel di tutte le liste | — | ⬜ |
| 4.7 | 🔐 XML FatturaPA, stati e notifiche, canale di prova ([ADR 0017](decisioni/0017-canale-sdi.md)) | V17 | ⬜ |
| 4.8 | 🔐 Integrazione del canale SDI scelto | — | ⏸️ in attesa della scelta del fornitore |
| 4.9 | Scadenzario: incassi, pagamenti, insoluti | V18 | ⬜ |
| 4.10 | Magazzino leggero: giacenze con operazioni a delta, carichi e scarichi dai documenti | V19 | ⬜ |

## Fase 5 — Robustezza e requisiti per vendere

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 5.1 | 🔐 Registro delle modifiche sui dati di dominio (oggi `platform_audit` copre solo la console) | V20 | ⬜ |
| 5.2 | 🔐 Limitazione della frequenza delle richieste (login e `POST /api/ai/chat` in particolare) | — | ⬜ |
| 5.3 | 🔐 `Content-Security-Policy` e `frame-ancestors` in Caddy e nginx | — | ⬜ |
| 5.4 | Gestione utenti da parte dell'azienda cliente: inviti via email, ruoli, disattivazione | — | ⬜ |
| 5.5 | Import guidato da CSV/Excel con mappatura delle colonne e anteprima | — | ⬜ |
| 5.6 | Documenti legali: informativa, condizioni d'uso, **accordo come responsabile del trattamento (art. 28)**, registro dei trattamenti | — | ⬜ |
| 5.7 | 🔐 GDPR operativo: export e cancellazione dei dati di un interessato, politica di conservazione | — | ⬜ |
| 5.8 | Versione visibile nell'app e changelog (la prima domanda del supporto è «che versione hai?») | — | ⬜ |
| 5.9 | Firma degli installer Windows e aggiornamento automatico dell'app desktop | — | ⬜ |
| 5.10 | Ambiente di prova separato dalla produzione, per provare le migrazioni su dati realistici | — | ⬜ |
| 5.11 | Test end-to-end con Playwright, incluso il ciclo offline → online | — | ⬜ |
| 5.12 | 🔐 Ruoli multipli per utente: un account può avere insieme un ruolo d'account (`admin`/`operator`/`viewer`) e un ruolo di cantiere (`capocantiere`/`caposquadra`/`operaio`/`amministrativo`) — Keycloak, `PlatformDtos.role` da stringa a lista, selettore multiplo in console (decisione presa il 25/11/2026, vedi "Decisioni prese" più sotto) | — | ⬜ |

## Fase 6 — Lato SaaS

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 6.1 | Piani e listino: `Features` oggi contiene una sola funzionalità, quindi non c'è nulla da vendere a scaglioni | — | ⬜ |
| 6.2 | 🔐 Registrazione self-service dell'azienda e periodo di prova | V21 | ⬜ |
| 6.3 | 🔐 Pagamenti e ciclo dell'abbonamento (prova → attivo → sospeso → riattivato) | V22 | ⬜ |
| 6.4 | Report e cruscotto: fatturato per mese e cliente, **marginalità per commessa**, ore per persona | — | ⬜ |
| 6.5 | 🔐 Portale del cliente finale con link firmato: vede il preventivo e lo accetta, segue la commessa | V23 | ⬜ |
| 6.6 | 🔐 API pubblica con OpenAPI, chiavi per tenant, webhook | V24 | ⬜ |

## Fase 7 — Assistente AI

L'impianto è già pronto (quota, tracciamento dei costi, conferma esplicita delle scritture): mancano
i tool di dominio e un provider reale.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 7.1 | Provider AI reale al posto di `NoopAiProvider` | — | ⬜ |
| 7.2 | Tool di dominio per l'assistente (wrapper sottili sui servizi, permessi dell'utente) | — | ⬜ |
| 7.3 | Lettura automatica dei documenti: foto di una fattura fornitore → righe estratte → prima nota precompilata | — | ⬜ |
| 7.4 | Rapportino dettato a voce: offline registra, online trascrive e compila | — | ⬜ |
| 7.5 | Ricerca semantica su documenti e allegati (pgvector) | — | ⬜ |

---

> **Aggiornamento**: il nucleo rapportino/ore/assegnazione delle Fasi A–I (ex A.2–A.4, A.7) è stato
> confrontato con un piano tecnico più solido ricevuto dall'utente ("Analisi_Completa.md", v0.1,
> 25/11/2026) e **superato** da esso: il modello di riferimento per quel nucleo sono ora le epiche
> GitHub [#40](https://github.com/giorgiorest/gestionale/issues/40)–[#45](https://github.com/giorgiorest/gestionale/issues/45)
> (R0–R5), non più le vecchie voci. Il resto delle Fasi A–I (estensioni di A, B–H) è stato a sua
> volta trasformato in epiche/storie GitHub (#61–#113), elencate nelle intestazioni di fase sotto.
>
> **Aggiornamento (25/11/2026)**: una nuova estrazione delle stringhe dall'artifact "CivilBeefy
> Beta" (il fetch diretto della pagina non bastava, essendo una SPA client-side) ha rivelato voci
> non emerse nella prima analisi: eventi contrattuali dell'appalto (riserve, accordi bonari,
> sospensioni, varianti, fideiussioni/polizze, casellario fornitori, ordini di servizio,
> corrispondenza/PEC), diventati la nuova **Fase I** sotto, e una lista in economia collegata al SAL
> (F.7). Epica Fase I e storie: [#130](https://github.com/giorgiorest/gestionale/issues/130)–[#139](https://github.com/giorgiorest/gestionale/issues/139).

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
| A.9 | 🔐 Scheda anagrafica estesa del personale (contratto, IBAN, taglie DPI/divisa, documenti e relative scadenze), visibile solo a manager/amministrativo — [#73](https://github.com/giorgiorest/gestionale/issues/73) | da assegnare | ⬜ |
| A.10 | 🔐 Corsi di formazione sicurezza: catalogo (RSPP, ASPP, preposto, PLE, gru, DPI, ecc.), scadenze calcolate, upload attestati — [#74](https://github.com/giorgiorest/gestionale/issues/74) | da assegnare | ⬜ |
| A.11 | 🔐 Lettura automatica AI degli attestati (foto/PDF → corso e data riconosciuti) — [#75](https://github.com/giorgiorest/gestionale/issues/75) | — | ⬜ |
| A.12 | Cruscotto scadenze personale (documenti + corsi) con filtri per orizzonte temporale (scadute/30/60/90 gg) — [#72](https://github.com/giorgiorest/gestionale/issues/72) | — | ⬜ |
| A.13 | 🔐 Falla pre-esistente notata in [ADR 0021](decisioni/0021-storico-costi-manodopera.md) (introdotta da #47/#51, non da #53): `hourlyCostOverride` (costo orario, contestuale a una commessa) esposto da `GET /api/commesse/{id}/resource-assignments` a chi ha `resource-assignments:read` ma non `labor-cost:read` (`caposquadra`, `operaio`) — chiusa in [PR #125](https://github.com/giorgiorest/gestionale/pull/125) nascondendo il campo nella risposta | — | ✅ |

## Fase B — Attrezzature e mezzi

[Epica GitHub #62](https://github.com/giorgiorest/gestionale/issues/62).

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| B.1 | Anagrafica attrezzature/mezzi: inventario di cantiere, parco di proprietà, import da Excel — [#76](https://github.com/giorgiorest/gestionale/issues/76) | da assegnare | ⬜ |
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
| D.5 | Dettatura vocale per l'inserimento rapido del rapportino (offline registra, online trascrive) — [#92](https://github.com/giorgiorest/gestionale/issues/92) | — | ⬜ |

## Fase E — Fornitori avanzati

[Epica GitHub #65](https://github.com/giorgiorest/gestionale/issues/65). E.2/E.5/E.6: AI sui
documenti, **rimandata a dopo le fondamenta** (vedi "Decisioni prese").

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
condiviso sui costi, non duplicato. F.5: AI sui documenti, **rimandata a dopo le fondamenta**
(vedi "Decisioni prese").

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| F.1 | Costi industriali — [#99](https://github.com/giorgiorest/gestionale/issues/99) | da assegnare | ⬜ |
| F.2 | Contabilità attiva — [#100](https://github.com/giorgiorest/gestionale/issues/100) | da assegnare | ⬜ |
| F.3 | Contabilità passiva — [#101](https://github.com/giorgiorest/gestionale/issues/101) | da assegnare | ⬜ |
| F.4 | 🔐 SAL dei subappaltatori: stato da confermare/confermato con blocco, log eventi, allegati — [#102](https://github.com/giorgiorest/gestionale/issues/102) | da assegnare | ⬜ |
| F.5 | 🔐 Lettura automatica AI di SAL e registro di contabilità (PDF/Excel/Word/foto) — [#103](https://github.com/giorgiorest/gestionale/issues/103) | — | ⬜ |
| F.6 | Vista SAL per ditta subappaltatrice — [#104](https://github.com/giorgiorest/gestionale/issues/104) | — | ⬜ |
| F.7 | Lista in economia collegata a un SAL — [#139](https://github.com/giorgiorest/gestionale/issues/139) | da assegnare | ⬜ |

## Fase G — Pianificazione (Gantt/WBS)

[Epica GitHub #67](https://github.com/giorgiorest/gestionale/issues/67). Complementare (tempo),
non sovrapposta, all'incremento 12 del piano tecnico ("WBS e budget", costo,
[#58](https://github.com/giorgiorest/gestionale/issues/58)): condividono `WorkActivity.parentId`
(incremento 2, [#48](https://github.com/giorgiorest/gestionale/issues/48)). G.6: AI sui documenti,
**rimandata a dopo le fondamenta** (vedi "Decisioni prese").

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

## Fase I — Vicende contrattuali dell'appalto

[Epica GitHub #130](https://github.com/giorgiorest/gestionale/issues/130). Complementare a F
(contabilità/SAL) e H (archivio documentale): qui stanno gli eventi legali/contrattuali
dell'esecuzione, non i costi né l'archivio generico.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| I.1 | Riserve iscritte dall'impresa: numero, oggetto, importo richiesto, documenti — [#131](https://github.com/giorgiorest/gestionale/issues/131) | da assegnare | ⬜ |
| I.2 | Accordi bonari che compongono una o più riserve — [#132](https://github.com/giorgiorest/gestionale/issues/132) | da assegnare | ⬜ |
| I.3 | Sospensioni e riprese dei lavori — [#133](https://github.com/giorgiorest/gestionale/issues/133) | da assegnare | ⬜ |
| I.4 | Varianti e perizie — [#134](https://github.com/giorgiorest/gestionale/issues/134) | da assegnare | ⬜ |
| I.5 | 🔐 Fideiussioni e polizze (fideiussione definitiva, polizza CAR, lettera di impegno) con scadenze — [#135](https://github.com/giorgiorest/gestionale/issues/135) | da assegnare | ⬜ |
| I.6 | 🔐 Casellario e carichi pendenti dei fornitori/subappaltatori (esito, data verifica) — [#136](https://github.com/giorgiorest/gestionale/issues/136) | da assegnare | ⬜ |
| I.7 | Ordini di servizio della stazione appaltante — [#137](https://github.com/giorgiorest/gestionale/issues/137) | da assegnare | ⬜ |
| I.8 | 🔐 Corrispondenza e PEC del cantiere/contratto — [#138](https://github.com/giorgiorest/gestionale/issues/138) | da assegnare | ⬜ |

---

## File che tutte le pull request toccano

Sono i punti di conflitto quando ci sono più branch aperti insieme. Chi ne tocca uno lo dichiara
nella descrizione della PR.

- `apps/api/src/main/resources/db/migration/` — vedi [ADR 0013](decisioni/0013-numerazione-migrazioni.md)
- `apps/api/.../auth/Permission.java` e `licensing/Features.java`
- `apps/web/src/app/app.routes.ts`
- `apps/web/src/app/db/local-db.ts` (nuova `version()`, mai modificare quelle esistenti)
- `apps/web/src/app/core/privacy/local-data.service.ts` (`clear()`)
- `infra/deploy/oracle/.env.example` e `compose.yml`
- questo file

## Decisioni ancora da prendere

| Cosa | Chi | Blocca |
|------|-----|--------|
| Fornitore del canale SDI ([ADR 0017](decisioni/0017-canale-sdi.md)) | titolare del prodotto | PR 4.8 |
| Fornitore dell'archivio allegati in SaaS ([ADR 0016](decisioni/0016-archivio-allegati.md)) | titolare del prodotto | PR 2.4 in produzione (lo sviluppo procede con MinIO) |

## Decisioni prese (25/11/2026)

- **Ruoli multipli per utente**: confermato. Un utente potrà avere insieme un ruolo d'account
  (`admin`/`operator`/`viewer`) e un ruolo di cantiere (`capocantiere`/`caposquadra`/`operaio`/
  `amministrativo`). Cambio più grande di quanto bastava per A.1: tracciato come voce **5.12** più
  sopra (conviene farla presto, prima che altre fasi assegnino un ruolo di cantiere assumendo la
  scelta singola).
- **AI sui documenti**: rimandata a dopo le fondamenta. Le sei voci (E.2, E.5, E.6, C.3, A.11,
  F.5, G.6) restano in questo file ma si lavorano per ultime (Onda 10 di
  `docs/ordine-esecuzione.md`).
- **Dettatura vocale (D.5)**: confermata da costruire presto, insieme al resto della Fase D (non
  rimandata).

## Note di sequenza (Fasi A–I)

- Risorse e squadre (2.3) e Commesse/cantieri (3.1) sono **già costruite**: tutte le Fasi A–I si
  appoggiano su di esse.
- Il nucleo rapportino/ore/assegnazione (ex A.2–A.4, A.7) è **superato** dal piano tecnico allegato
  ("Analisi_Completa.md"): seguire le epiche GitHub
  [#40](https://github.com/giorgiorest/gestionale/issues/40) (R0 — Foundation),
  [#41](https://github.com/giorgiorest/gestionale/issues/41) (R1 — Rapportino MVP),
  [#42](https://github.com/giorgiorest/gestionale/issues/42) (R2 — Controllo manodopera:
  [#52](https://github.com/giorgiorest/gestionale/issues/52) ✅,
  [#53](https://github.com/giorgiorest/gestionale/issues/53) ✅ (PR
  [#124](https://github.com/giorgiorest/gestionale/pull/124)),
  [#54](https://github.com/giorgiorest/gestionale/issues/54) ✅) come
  modello di riferimento, non le vecchie voci.
- Le Fasi B, C, D possono procedere in parallelo alla Fase A, ma D.4 (rapportino/archivio) condivide
  la UI del rapportino con le storie [#49](https://github.com/giorgiorest/gestionale/issues/49)–[#51](https://github.com/giorgiorest/gestionale/issues/51): tenerle vicine.
- La Fase E estende i fornitori di base (2.2, non ancora costruita): va fatta dopo.
- La Fase F (SAL, contabilità) ha senso dopo l'incremento 12 del piano tecnico ("WBS e budget",
  [#58](https://github.com/giorgiorest/gestionale/issues/58)) e dopo la fatturazione di base
  (Fase 4).
- La Fase G (Gantt/WBS) è indipendente ma più utile una volta che esistono le commesse (già fatte)
  e i costi industriali (F.1).
