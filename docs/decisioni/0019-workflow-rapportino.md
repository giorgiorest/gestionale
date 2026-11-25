# ADR 0019 — Workflow del rapportino (submit/close) e snapshot del costo manodopera

- **Stato**: accettata
- **Data**: 2026-11-25

## Contesto

Le storie #49 e #50 hanno costruito la testata del rapportino (`DailyReport`, sempre `DRAFT`) e le
sue righe di ore (`WorkEntry`). La storia #51 ([GitHub #51](https://github.com/giorgiorest/gestionale/issues/51))
aggiunge le transizioni vere: `DRAFT → SUBMITTED → CLOSED`, il blocco delle scritture ordinarie
fuori da `DRAFT`, e lo snapshot del costo manodopera alla chiusura. Tre scelte non ovvie dal
codice esistente.

## Decisione

1. **Endpoint dedicati, non un `PATCH` sullo stato.** `POST /api/daily-reports/{id}/submit` e
   `/close`, come chiesto dall'issue: lo stato non è un campo dell'`Input` di `DailyReportDto`
   (a differenza di `Commessa.stato`, che oggi si cambia con un `PUT` libero — un precedente che
   questa storia **non** replica, apposta).
2. **`submit`/`close` non sono idempotenti**, a differenza di `PUT`/`DELETE`. Una chiamata su un
   rapportino che non è nello stato di partenza giusto (es. `close` due volte, o prima di
   `submit`) fallisce sempre con `409`, non è un no-op silenzioso. Motivo: la Definition of Done
   chiede che due richieste concorrenti sullo stesso rapportino non producano uno stato
   incoerente, e che una delle due falisca con `409`. Con una semantica idempotente, la richiesta
   che arriva *dopo* che l'altra ha già vinto vedrebbe lo stato già raggiunto e risponderebbe
   `200` in silenzio — esattamente il conflitto silenzioso che l'issue vuole escludere. Con la
   semantica non idempotente, la richiesta che perde la gara fallisce sempre con `409`, sia che la
   rilevi il controllo applicativo (legge lo stato già cambiato) sia che la rilevi il blocco
   ottimistico `@Version` (legge lo stesso stato dell'altra, perde il conflitto al flush) — stessa
   eccezione (`OptimisticLockingFailureException`), stesso codice. Il test di concorrenza
   (`DailyReportControllerTest.concurrentCloseRequestsOnlyOneSucceeds`) non deve indovinare quale
   dei due percorsi scatta: in entrambi i casi il risultato è `200`/`409`.
3. **Una sola eccezione, `InvalidDailyReportStateException` (409)**, per ogni scrittura bloccata
   da uno stato non `DRAFT`: le transizioni invalide in `DailyReport.submit/close`, `PUT`/`DELETE`
   sulla testata in `DailyReportService`, `PUT`/`DELETE` sulle righe in `WorkEntryService`. Un
   rapportino `SUBMITTED` o `CLOSED` non è modificabile da **nessun** endpoint ordinario (DoD
   dell'issue): il controllo sta nell'entità (`DailyReport.requireDraft()`), non ripetuto a mano
   in ogni service.
4. **Permesso riusato, non dedicato**: `submit`/`close` richiedono `daily-reports:write`, lo
   stesso della testata. La tabella permessi dell'issue (RW admin/operator/capocantiere, R viewer,
   — caposquadra/operaio) coincide esattamente con la distribuzione attuale di quel permesso: un
   `daily-reports:close` dedicato richiederebbe di assegnarlo esattamente ai ruoli che hanno già
   `daily-reports:write`, cioè lo stesso controllo con un codice in più da mantenere.
5. **Snapshot del costo manodopera (`work_entry_cost_snapshots`, V13)**: scritto solo dal server,
   in `WorkEntryService.snapshotCostsForClose`, chiamato da `DailyReportService.close()` nella
   stessa transazione della transizione di stato (rollback comune). Non è un'entità sincronizzata
   dal client: niente colonne di sincronizzazione (ADR 0018) — è un registro di sola lettura una
   volta creato, come `platform_audit` (V5), non un'anagrafica che il client aggiorna.
   - **Tariffa oraria**: letta da `ResourceAssignment.hourlyCostOverride` valida sulla
     commessa/data del rapportino (la stessa assegnazione che `WorkEntryService` già valida alla
     creazione della riga). Quando l'assegnazione non ha una tariffa (campo facoltativo) — e su
     `Resource` non esiste ancora una tariffa standard di fallback — `hourly_cost`/`labor_cost`
     restano `NULL`: il costo di quella riga è sconosciuto, non zero. Una tariffa standard sulla
     risorsa è una scelta di prodotto non ancora presa: non va inventata qui, va segnalata.
   - **Straordinario**: usa la stessa tariffa oraria delle ore normali. Non esiste ancora una
     maggiorazione definita per lo straordinario; altra scelta non presa, non inventata.
   - **Esposizione**: questa storia scrive lo storico ma non aggiunge un endpoint di lettura
     dedicato — non richiesto dall'issue. Lo consumerà la voce 3.5 della roadmap (riepiloghi
     ore/costi).
6. **Fuori scope: il wiring frontend.** L'issue non lo chiede (Da fare/DoD sono solo backend).
   `SyncService` oggi parla solo `create`/`update`/`delete` → `PUT`/`DELETE {entity}/{id}`
   (outbox offline-first): incastrare un'azione online-only come `submit`/`close` in quel modello
   — o trattarla come un'eccezione online-only, come la console di piattaforma — è una decisione
   di prodotto/architettura a parte, non da improvvisare in questa storia.

## Conseguenze

- Un client che reinvia una `submit`/`close` già riuscita (es. risposta persa per un problema di
  rete) riceve `409`, non `200`: a differenza della outbox (pensata per il reinvio idempotente),
  chi chiama queste due azioni deve trattare `409` come "probabilmente già fatto", non come un
  errore da segnalare all'utente. Rilevante solo quando (e se) il frontend le chiamerà (vedi punto
  6).
- Il costo manodopera di una riga senza tariffa nota resta sconosciuto per sempre (lo snapshot è
  immutabile): va risolto assegnando una tariffa all'assegnazione **prima** della chiusura, non
  dopo.

## Alternative scartate

- **`PATCH` libero sullo stato** (come `Commessa.stato` oggi): è esattamente il pattern che
  l'issue chiede di evitare («non un update libero del campo status»).
- **`submit`/`close` idempotenti** (no-op se già nello stato di arrivo): vedi punto 2 — avrebbe
  reso il test di concorrenza dipendente da quale richiesta arriva "prima" a leggere lo stato,
  senza garantire il `409` richiesto dalla Definition of Done.
- **Tariffa standard inventata su `Resource`** per evitare `NULL` nello snapshot: avrebbe
  nascosto un gap di dati reale dietro un numero inventato, in un campo che calcola un costo.
- **Permesso dedicato `daily-reports:close`**: vedi punto 4.
