# ADR 0020 — Endpoint di report (aggregazione, non lista)

- **Stato**: accettata
- **Data**: 2026-11-25

## Contesto

La storia #52 ([GitHub #52](https://github.com/giorgiorest/gestionale/issues/52)) aggiunge il
primo endpoint del progetto che non restituisce un elenco di record né un singolo record, ma
un'**aggregazione** su un periodo: il cruscotto ore di una commessa (vista settimanale, vista
mensile per risorsa, aggregazione per lavorazione). La storia #53 (costo manodopera, dipendente
da questa) avrà la stessa forma. Senza una decisione esplicita, ogni report rischia di riscoprire
da zero la stessa forma, o di forzarci dentro la convenzione delle liste (ADR 0014), che non è
pensata per questo.

## Decisione

1. **Non è una lista paginata.** ADR 0014 (`?page=&size=&sort=&q=`, `PageDto<T>`) resta per gli
   elenchi di anagrafiche; un report usa `?from=&to=` (periodo) e risponde con un oggetto che
   contiene le viste aggregate, non una pagina di record. Le due convenzioni non si mescolano:
   un report non ha `page`/`size`/`sort`/`q`, una lista non ha `from`/`to`.
2. **L'aggregazione è nella query, non in Java su dati non aggregati.** Il `GROUP BY` (somma delle
   ore) sta nella query JPQL/SQL, al livello più fine utile (qui: giorno/risorsa/lavorazione);
   un ulteriore raggruppamento più grossolano (qui: per settimana, per mese) su righe **già
   somma** in una singola query filtrata per tenant/commessa/periodo è accettabile in Java — non
   è l'aggregazione "a mano" che la Definition of Done vieta, perché non itera i record grezzi.
3. **Nessuna copia locale, nessuna outbox.** Un report è una vista derivata, ricalcolata a ogni
   richiesta: non è un'entità di dominio da sincronizzare offline (stesso confine di ADR 0014 tra
   paginazione e sincronizzazione). Il frontend lo richiama con una `HttpClient.get` diretta; se il
   server non è raggiungibile il report semplicemente non si aggiorna, non c'è una copia da
   mostrare.
4. **Nessun permesso dedicato quando l'esistente già coincide.** Il permesso dell'endpoint è
   quello dei dati che aggrega (qui `work-entries:read`), non uno nuovo — a meno che l'insieme di
   ruoli autorizzati debba davvero differire da quello dei dati sorgente.
5. **I nomi delle anagrafiche (risorsa, lavorazione, …) non sono nel payload del report**: solo
   gli id. Il frontend li risolve dalla copia locale già sincronizzata delle anagrafiche
   (`resources`, `work-activities`, …), come già fa per le righe di ore (#50).

## Conseguenze

- La #53 (costo manodopera) può riusare questa forma senza riaprire le stesse domande: stessa
  query-side aggregation, stesso confine con ADR 0014, stessa assenza di copia locale.
- Un report che diventasse grande (molte settimane, molte risorse) potrebbe in futuro avere
  bisogno di un limite sul periodo (`to - from` massimo) o di paginazione propria: non è un
  problema di questa storia, va aggiunto quando emerge.

## Alternative scartate

- **Estendere ADR 0014 ai report** (aggiungere `from`/`to` alla stessa convenzione delle liste):
  scartata perché un report non è una pagina di record, e forzarlo nella stessa forma (`PageDto`,
  `sort`) avrebbe reso l'endpoint più complesso senza bisogno.
- **Copia locale del report in Dexie**: scartata per questa storia. Un report è ricalcolato dal
  server a ogni richiesta; tenerne una copia offline richiederebbe invalidarla ogni volta che
  cambiano le ore sottostanti, un problema che le entità di dominio non hanno (la outbox sincronizza
  i dati, non le viste derivate da essi).
