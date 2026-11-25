# ADR 0021 — Storico costi: tariffa base, fallback e permesso dedicato

- **Stato**: accettata
- **Data**: 2026-11-25

## Contesto

La storia #51 ([GitHub #51](https://github.com/giorgiorest/gestionale/issues/51)) ha scritto
`WorkEntryCostSnapshot` (V13) ma con `hourlyCost`/`laborCost` sempre `NULL`: `Resource` non aveva
nessuna tariffa oraria, solo l'override contestuale di `ResourceAssignment.hourlyCostOverride`
(V9), opzionale. La storia #53 ([GitHub #53](https://github.com/giorgiorest/gestionale/issues/53))
chiude quel gap con una tariffa oraria base sulla risorsa, un fallback nello snapshot, e un
endpoint KPI (ore, costo totale, costo per lavorazione) che legge lo storico già congelato.

Il punto non ovvio, esplicito nell'issue: il costo orario è un dato sensibile (retribuzione
implicita), diverso dagli altri campi di `Resource` (nome, cognome) che chiunque con
`resources:read` legge oggi — `operaio` e `amministrativo` compresi.

## Decisione

1. **Tariffa base su `Resource` (`standard_hourly_cost`, V14, nullable)**, prima `ALTER TABLE` del
   repository su una tabella di dominio già esistente (le colonne di costo precedenti, V9/V13,
   sono nate dentro una `CREATE TABLE`). Nullable come l'override: una risorsa senza tariffa nota
   resta a costo sconosciuto, non zero.
2. **Permesso dedicato (`labor-cost:read`/`labor-cost:write`), non `resources:read`/`write`.**
   `resources:read` lo hanno tutti i ruoli, inclusi `operaio` (vedrebbe il proprio costo e quello
   dei colleghi) e `amministrativo` (che per definizione di ruolo non deve mai toccare contabilità/
   payroll). Il campo non entra in `ResourceDto`/`ResourceController`: vive in due endpoint nuovi
   (`GET`/`PUT /api/resources/{id}/labor-cost`) con `@PreAuthorize` proprio, cosicché non arrivi
   nemmeno alla sincronizzazione offline di `resources` (Dexie), che oggi serve chiunque abbia
   `resources:read`.
3. **Assegnazione del permesso, per coerenza con ciò che ogni ruolo gestisce già oggi**:
   - `ADMIN`: tutto (`EnumSet.allOf`, nessuna eccezione).
   - `OPERATOR`/`CAPOCANTIERE`: lettura e scrittura. Hanno già `resource-assignments:write`, cioè
     possono già impostare un costo (l'override contestuale alla commessa): la tariffa base è la
     stessa responsabilità, non un dato nuovo da proteggere diversamente.
   - `VIEWER`: sola lettura, per lo stesso motivo (ha già `resource-assignments:read`).
   - `CAPOSQUADRA`, `OPERAIO`, `AMMINISTRATIVO`: nessuno dei due. `OPERAIO` non deve vedere il
     proprio costo né quello dei colleghi; `AMMINISTRATIVO` è "mai contabilità" per definizione di
     ruolo; `CAPOSQUADRA` coordina la squadra sul campo, non la gestisce a livello economico (a
     differenza di `CAPOCANTIERE`, che ha anche `resources:write`).
4. **Fallback nello snapshot, non ricalcolo**: `WorkEntryService.snapshotFor()` cerca prima
   l'override dell'assegnazione, poi la tariffa base della risorsa; resta `NULL` solo se nessuna
   delle due esiste. Si applica una sola volta, al momento della chiusura: lo snapshot resta
   immutabile, quindi un cambio successivo (override o tariffa base) non lo altera mai — il DoD
   ("il consuntivo chiuso non cambia se il costo cambia dopo") è soddisfatto per costruzione,
   senza bisogno di altra logica né di un nuovo "trigger di revisione" oltre a questo: un cambio al
   modello di costing/payroll andrebbe comunque solo nei nuovi snapshot, mai in quelli già scritti.
5. **Nuovo modulo `laborcostreport/`, non un endpoint dentro `hoursreport/`**: permesso diverso
   (`labor-cost:read` contro `work-entries:read`) e tabella diversa (`work_entry_cost_snapshots`,
   già aggregabile direttamente, non le righe grezze di `work_entries` incrociate con le
   assegnazioni). Stessa forma di report di ADR 0020 (scoped a commessa, `?from=&to=`,
   aggregazione nella query, nessuna copia locale, solo id nel payload): la #53 la riusa senza
   riaprirne le domande, non introduce una terza convenzione.
6. **La `SUM` SQL gestisce il NULL da sola**: `sum(s.laborCost)` raggruppato per lavorazione
   restituisce `NULL` se tutte le righe del gruppo hanno costo sconosciuto, altrimenti la somma
   delle righe note (comportamento standard dell'aggregazione SQL, senza `CASE WHEN`). Il totale di
   periodo in `LaborCostReportService` applica la stessa regola sommando i gruppi già aggregati
   (ADR 0020 punto 2: raggruppamento più grossolano in Java su righe già-somma, non sui dati
   grezzi).
7. **Il report include solo rapportini chiusi**: unici con uno snapshot. Un rapportino ancora
   `DRAFT`/`SUBMITTED` semplicemente non compare — comportamento naturale del dato (lo snapshot
   nasce alla chiusura), non un filtro aggiunto apposta.

## Conseguenze

- Chi ha `labor-cost:write` ma non `resources:write` (nessun ruolo attuale, ma possibile in
  futuro) può impostare la tariffa base senza poter modificare nome/cognome della risorsa: i due
  permessi sono indipendenti anche se oggi i ruoli che li hanno coincidono.
- Un costo "parzialmente noto" (alcune righe del gruppo con tariffa, altre senza) produce un totale
  che è la somma delle sole righe note, non segnalato come incompleto: stessa scelta già presa per
  lo snapshot singolo (NULL ≠ zero, ma nessun contatore di "ore a costo sconosciuto" inventato qui,
  perché non richiesto dall'issue).

## Nota su una falla pre-esistente, risolta in una PR dedicata

`CAPOSQUADRA` e `OPERAIO` avevano già `resource-assignments:read` e quindi vedevano già
`hourlyCostOverride` tramite `GET /api/commesse/{id}/resource-assignments` — un costo, anche se
contestuale a una commessa invece che la tariffa base della persona. Era una falla precedente a
questa storia (introdotta da #47/#51, non da #53): non è stata sistemata qui per non allargare lo
scope di questa PR a un endpoint diverso con una storia già chiusa.

Risolta in una PR successiva, dedicata, con la seconda delle due opzioni elencate sopra:
`ResourceAssignmentController.listByCommessa()` nasconde `hourlyCostOverride` (lo imposta a
`null`) nella risposta quando il chiamante non ha `labor-cost:read`, anche se ha
`resource-assignments:read`. Non la prima (endpoint/DTO dedicato come per la tariffa base di
`Resource`) perché, a differenza di quella, `hourlyCostOverride` è parte di un'entità sincronizzata
offline (`ResourceAssignment`, outbox): separarlo in un endpoint online-only avrebbe richiesto
toccare anche la scrittura via outbox, uno scope più ampio della sola falla di lettura. La
scrittura non ne aveva bisogno: ogni ruolo con `resource-assignments:write` (`ADMIN`, `OPERATOR`,
`CAPOCANTIERE`) ha già anche `labor-cost:write`.

## Alternative scartate

- **Campo su `ResourceDto`, dietro `resources:read`/`write`**: scartata — avrebbe esposto il costo
  a `operaio` e `amministrativo`, esattamente il rischio che l'issue segnala.
- **Riuso di `hoursreport/`**: scartata — permesso diverso dai dati sorgente (punto 5), avrebbe
  reso un controller con due permessi diversi su endpoint dello stesso modulo, meno leggibile di un
  modulo a parte.
- **Contatore "ore a costo sconosciuto" nel payload del report**: scartata per questa storia —
  l'issue chiede "ore, costo totale, costo per lavorazione", non una scomposizione del perché il
  totale possa essere parziale; si aggiunge quando (e se) servirà.
