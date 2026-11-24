# Roadmap di Civil Beefy

Verticale di prodotto: **imprese edili e di cantiere** ([ADR 0012](decisioni/0012-verticale-edilizia.md)).

Questo file è la **fonte di verità** del lavoro da fare: non va tenuto in chat né in una issue. Una
pull request = una voce di questa tabella. Se una voce non sta in una sessione di lavoro, va spezzata
qui prima di cominciare.

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

Stato: ⬜ da fare · 🟨 in corso · ✅ fatto · ⏸️ in attesa di una decisione esterna.
🔐 = passa da `/security-review`. La colonna *Migr.* è il numero Flyway **previsto**: è un
promemoria contro le collisioni, non una prenotazione ([ADR 0013](decisioni/0013-numerazione-migrazioni.md)).

---

## Fase 0 — Processo di sviluppo

Si ripaga entro la seconda PR: serve a non ri-scoprire il progetto a ogni sessione.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 0.1 | Ricettario in CLAUDE.md, questa roadmap, ADR 0012–0017, permessi di progetto, controllo delle migrazioni in CI | — | 🟨 |
| 0.2 | Catena di fornitura: Dependabot, `npm audit` e controllo dipendenze nella CI, scansione delle immagini, elenco delle licenze delle dipendenze | — | ⬜ |
| 0.3 | ESLint e Prettier nella CI web, budget sulla dimensione del bundle | — | ⬜ |
| 0.4 | Osservabilità: log strutturati JSON con `tenant_id` e id della richiesta, metriche Actuator/Prometheus, avviso quando la salute cade | — | ⬜ |
| 0.5 | **Backup fuori sito** (l'archivio oggi resta sulla stessa macchina) e **prova di ripristino** documentata | — | ⬜ |
| 0.6 | Hook `SessionStart`: branch, PR aperta, prossima voce di roadmap, primo numero Flyway libero | — | ✅ |

> La 0.5 è il rischio singolo più grande del progetto: un backup mai ripristinato non è un backup.

## Fase 1 — Fondamenta della sincronizzazione

**Da fare prima di aggiungere entità nuove**: ogni entità aggiunta senza sincronizzazione vera è
debito da pagare moltiplicato per il numero di entità.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 1.1 | Cancellazione logica (`deleted_at`), `DELETE /api/customers/{id}` idempotente, `@Version` per il blocco ottimistico, `created_by`/`updated_by` ([ADR 0018](decisioni/0018-cancellazione-logica.md)) | V6 | ✅ |
| 1.2 | `GET /api/sync/changes` con cursore per entità (`updated_at` + id) e tombstones | — | ⬜ |
| 1.3 | `pullChanges` nel client: cursore in `syncState`, applicazione locale, schermata «modifiche non inviate» | — | ⬜ |
| 1.4 | Liste paginate, ordinate e ricercabili ([ADR 0014](decisioni/0014-liste-paginate.md)) su `customers`, con gli indici | V7 | ⬜ |
| 1.5 | Conflitti: `If-Match`/409, risoluzione nell'interfaccia | — | ⬜ |

## Fase 2 — Anagrafiche del mestiere

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 2.1 | Anagrafica clienti completa: ragione sociale, P.IVA, codice fiscale, indirizzo, PEC, codice destinatario SDI, condizioni di pagamento | V8 | ⬜ |
| 2.2 | Fornitori, sullo stesso schema | V9 | ⬜ |
| 2.3 | Risorse e squadre (dipendenti che compilano i rapportini), collegate alla rubrica `app_users` | V10 | ⬜ |
| 2.4 | 🔐 Allegati lato backend: tabella con RLS, archivio compatibile S3, URL firmati, MinIO in sviluppo ([ADR 0016](decisioni/0016-archivio-allegati.md)) | V11 | ⬜ |
| 2.5 | Allegati lato client: caricamento offline, foto dal telefono, cache selettiva, svuotamento all'uscita | — | ⬜ |

## Fase 3 — Il cuore: commesse e rapportini

È il differenziante del prodotto: funziona in cantiere, senza campo, dal telefono.

| PR | Contenuto | Migr. | Stato |
|----|-----------|-------|-------|
| 3.1 | Commesse/cantieri: modello, stati, anagrafica, collegamento al cliente | V12 | ⬜ |
| 3.2 | Rapportino giornaliero lato backend: ore per persona, materiali, note, allegati | V13 | ⬜ |
| 3.3 | Rapportino sul telefono: interfaccia per schermo piccolo, compilazione offline, foto | — | ⬜ |
| 3.4 | Firma grafometrica sul rapportino (canvas, immagine allegata, impronta e orario) | — | ⬜ |
| 3.5 | Riepiloghi: ore per commessa, per persona, per periodo; export | — | ⬜ |
| 3.6 | Pianificazione squadre: calendario settimanale «chi va dove», sincronizzato sul telefono | V14 | ⬜ |

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
