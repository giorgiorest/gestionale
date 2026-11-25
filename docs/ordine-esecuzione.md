# Ordine di esecuzione consigliato

Incrocia `docs/roadmap.md`, `docs/roadmap-alex.md` e gli incrementi del piano tecnico
("Analisi_Completa.md", epiche GitHub [#40](https://github.com/giorgiorest/gestionale/issues/40)–[#45](https://github.com/giorgiorest/gestionale/issues/45))
per rispondere a una domanda che nessuno dei due file da solo copre: **in che ordine conviene
lavorare le ~110 voci rimanenti per finire prima**, rispettando le dipendenze reali e
parallelizzando il resto.

Questo file **non è una fonte di verità sullo stato** (quella resta nei due file sopra e nelle
issue): è solo un'indicazione di sequenza, pensata per decidere quali sessioni avviare insieme.
Non va aggiornato voce per voce; va riletto e corretto se l'ordine reale si discosta parecchio
da qui.

Dentro ogni onda, le voci **senza freccia tra loro sono parallelizzabili** (sessioni cloud
simultanee); una freccia (→) indica dipendenza diretta, da fare in sequenza.

## Onda 0 — Decisioni, zero codice

Gratis e bloccanti: ogni giorno in cui restano aperte rischia di far scrivere codice su
un'assunzione sbagliata (vedi le tabelle "Decisioni ancora da prendere" nei due file).

- Ruolo d'account vs ruolo di cantiere (multiplo o no) — blocca parte di Fase A
- AI sui documenti: differenziante da subito o estensione da rimandare — blocca E.2, E.5, E.6, C.3, A.11, F.5, G.6
- Dettatura vocale: stesso bivio — blocca D.5
- Fornitore canale SDI — blocca 4.8 (roadmap.md)
- Fornitore archivio allegati in SaaS — non blocca lo sviluppo (si procede con MinIO), solo la produzione

## Onda 1 — Fondamenta non ancora fatte

`roadmap.md` Fase 0 e Fase 1. **Da fare prima di tutto**: ogni entità nuova costruita senza
sincronizzazione vera, o senza backup verificato, è debito moltiplicato per il numero di entità.

Parallelizzabili tra loro:
- 0.2 Catena di fornitura (Dependabot, npm audit, scansione immagini)
- 0.3 ESLint/Prettier CI, budget bundle
- 0.4 Osservabilità (log, metriche, avvisi)
- 0.5 Backup fuori sito + prova di ripristino — **farla qui, non per ultima**: è il rischio singolo più grande del progetto (lo dice `roadmap.md`)

In sequenza (ognuna dipende dalla precedente):
- 1.2 → 1.3 → 1.4 → 1.5

## Onda 2 — Anagrafiche di base

`roadmap.md` Fase 2 (2.3 già fatta). **2.2 (Fornitori) sblocca tutta la Fase E** di
`roadmap-alex.md`: darle priorità sulle altre di questa onda.

- 2.1 Clienti
- 2.2 Fornitori — priorità alta, sblocca Fase E
- 2.4 → 2.5 (allegati backend 🔐, poi client)

## Onda 3 — Il cuore: rapportini

`roadmap.md` Fase 3 (3.1 e parte di 3.2 già fatte) + resto di R1 (epica
[#41](https://github.com/giorgiorest/gestionale/issues/41)). Può girare **in parallelo** a
Onda 2 (moduli diversi), ma entrambe richiedono Onda 1 completata (sincronizzazione).

- 3.2 (🟨 in corso): completare materiali/allegati sul rapportino
- [#50](https://github.com/giorgiorest/gestionale/issues/50) Work Entry → [#51](https://github.com/giorgiorest/gestionale/issues/51) Submit/Close
- 3.3 Rapportino telefono → 3.4 Firma grafometrica
- 3.5 Riepiloghi
- 3.6 Pianificazione squadre

## Onda 4 — Estensioni di cantiere indipendenti

`roadmap-alex.md` Fasi A (estensioni), B, C, D + incrementi tecnici 9–10
([#55](https://github.com/giorgiorest/gestionale/issues/55),
[#56](https://github.com/giorgiorest/gestionale/issues/56)). Dichiarate indipendenti tra loro
in `roadmap-alex.md` ("Note di sequenza"): **la coppia di voci da parallelizzare più
aggressivamente**, appena Onda 2/3 sono avviate.

- Fase A: A.5, A.6, A.8, A.9, A.10 → A.11, A.12 (parallele salvo A.11 dopo A.10)
- Fase B: B.1 → (B.2, B.3, B.4, B.5, B.6, B.7, B.8 → B.9) — B.1 prima di tutte, il resto parallelo
- Fase C: C.1 → C.2 → C.3 (in sequenza)
- Fase D: D.1 → D.2 → D.3 → D.4 → D.5 (se Onda 0 conferma la dettatura)
- Verificare prima di duplicare: gli incrementi 9/10 del piano tecnico si sovrappongono
  concettualmente a C/B — leggere le issue [#55](https://github.com/giorgiorest/gestionale/issues/55)/[#56](https://github.com/giorgiorest/gestionale/issues/56)
  prima di cominciare l'uno o l'altro

## Onda 5 — Fornitori avanzati e acquisti

`roadmap-alex.md` Fase E + incremento 11 ([#57](https://github.com/giorgiorest/gestionale/issues/57)).
Dopo 2.2 (fornitori base, Onda 2).

- E.1 → E.2 (se Onda 0 conferma l'AI) → E.3 → E.4 → E.5 → E.6
- [#57](https://github.com/giorgiorest/gestionale/issues/57) Fornitori/acquisti base

## Onda 6 — Fatturazione, contabilità e vicende contrattuali

`roadmap.md` Fase 4 + `roadmap-alex.md` Fase F e Fase I + incremento 12
([#58](https://github.com/giorgiorest/gestionale/issues/58), già nota dipendenza per F).

- 4.1 → 4.2 → 4.3 → 4.4 → 4.5 → 4.6 → 4.7 (4.8 in pausa, decisione esterna) → 4.9 → 4.10
- [#58](https://github.com/giorgiorest/gestionale/issues/58) WBS e budget → **poi** F.1 → (F.2, F.3) → F.4 → F.5 (se AI) → F.6 → F.7
- Fase I, in parallelo a F (tabelle diverse): (I.1 → I.2), I.3, I.4 (dopo G.2, Onda 7), I.5, I.6 (dopo E.1), I.7, I.8

## Onda 7 — Pianificazione (Gantt/WBS)

`roadmap-alex.md` Fase G. Dopo F.1 (costi industriali) e le commesse (già fatte).

- G.1 → G.2 → G.3
- G.4, G.5, G.6 (se AI) — parallele tra loro, indipendenti da G.3

## Onda 8 — Archivio documentale

`roadmap-alex.md` Fase H + incremento 13 ([#59](https://github.com/giorgiorest/gestionale/issues/59)).

- H.1, H.3 — da subito, indipendenti
- H.2 — dopo un minimo di I.5 (fideiussioni/polizze), per non duplicare il modello di contratto
- [#59](https://github.com/giorgiorest/gestionale/issues/59) Documenti/scadenze

## Onda 9 — Robustezza e requisiti per vendere

`roadmap.md` Fasi 5–6 + incremento 14 ([#60](https://github.com/giorgiorest/gestionale/issues/60)
Sicurezza MVP). Può slittare se il prodotto resta a uso interno più a lungo del previsto: non
blocca nessun'altra onda, a differenza delle precedenti.

- 5.1 … 5.11, 6.1 … 6.6 (dipendenze interne già nei due file)
- [#60](https://github.com/giorgiorest/gestionale/issues/60) Sicurezza MVP

## Onda 10 — Assistente AI reale

`roadmap.md` Fase 7 + le voci AI condizionali delle onde precedenti (se Onda 0 le confermano).
Ultima perché ha bisogno dei dati e dei tool di dominio costruiti da tutto il resto.

- 7.1 → 7.2 → (7.3, 7.4, 7.5)
- Le voci AI rimaste in sospeso: E.2, E.5, E.6, C.3, A.11, F.5, G.6 (quelle non già fatte prima, se la decisione di Onda 0 le confermava e qualcuna era rimasta indietro)
