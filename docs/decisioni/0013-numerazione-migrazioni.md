# ADR 0013 — Numerazione delle migrazioni Flyway con più branch aperti

- **Stato**: accettata
- **Data**: 2026-11-24

## Contesto

Le migrazioni sono sequenziali: `V1`…`V5`. Lo sviluppo procede per pull request brevi, a volte **più
di una aperta insieme**. Due branch che aggiungono entrambi `V6__…` si scontrano al merge.

Il problema non è il nome del file: è l'**ordine di applicazione**. Flyway, con `out-of-order`
disattivato (il default), rifiuta di applicare una versione inferiore a una già applicata. Quindi se
il branch A prenota `V6` e il branch B prenota `V7`, ma **B viene unito prima**, in produzione si
applica `V7` e poi `V6` viene rifiutata: il deploy si ferma. Prenotare i numeri in anticipo non
risolve, perché l'ordine di merge non è noto in anticipo.

## Decisione

1. **La numerazione resta sequenziale**: `V<n>__descrizione_breve.sql`, in italiano, minuscole e
   trattini bassi (es. `V6__anagrafiche_fiscali.sql`).
2. **Il numero è definitivo solo al merge.** Durante lo sviluppo si usa il primo numero libero; prima
   di unire, si riallinea il branch su `main` e, se nel frattempo quel numero è stato preso, si
   **rinomina** il file (è una rinomina, non una riscrittura: la migrazione non è ancora stata
   applicata da nessuna parte).
3. **La CI lo verifica**, così l'errore emerge nella pull request e non in produzione:
   `.github/scripts/controlla-migrazioni.sh` controlla che non ci siano versioni duplicate e che ogni
   migrazione nuova rispetto a `main` abbia una versione **maggiore** della più alta presente su
   `main`.
4. **`out-of-order` resta disattivato** (default di Flyway): una migrazione fuori ordine deve
   fallire in modo rumoroso, non essere applicata in silenzio.
5. Resta valida la regola di progetto: **una migrazione già applicata non si modifica mai**. Una
   correzione è una migrazione nuova.
6. [docs/roadmap.md](../roadmap.md) indica il numero *previsto* per ogni PR pianificata: è un
   promemoria per evitare collisioni prevedibili, non una prenotazione vincolante.

## Conseguenze

- Una pull request con migrazione, tenuta aperta a lungo, può richiedere una rinomina prima del
  merge. Costo: un `git mv` e una ricorsa della CI.
- Le pull request con migrazione conviene tenerle **corte e unirle presto**: è la stessa regola che
  vale per il resto del progetto.
- Lo script della CI è l'unico punto dove la regola è scritta in modo eseguibile: se la regola
  cambia, cambia lì.

## Alternative scartate

- **Versioni a timestamp** (`V20261124_1430__…`): rendono la collisione improbabile, ma non
  risolvono il problema vero (una migrazione può comunque finire applicata dopo una più recente che
  la presupponeva), perdono la leggibilità dell'ordine e sarebbero incoerenti con `V1`…`V5` già
  applicate in produzione.
- **`out-of-order = true`**: fa passare il deploy, ma applica `V6` dopo `V7`. Se `V7` presuppone
  `V6` — il caso normale — il database resta in uno stato che nessuno ha mai testato. È il modo
  peggiore di risolvere il problema: silenzioso.
- **Prenotazione rigida dei numeri nella roadmap**: non regge all'ordine di merge, come spiegato
  nel contesto.
