# ADR 0014 — Liste paginate, ordinate e ricercabili lato server

- **Stato**: accettata
- **Data**: 2026-11-24

## Contesto

`GET /api/customers` restituisce **tutte** le righe del tenant, senza paginazione, ordinamento né
ricerca lato server (`CustomerService.list()`). Con un'anagrafica reale (migliaia di clienti, poi
commesse, rapportini e documenti) la pagina diventa inutilizzabile e la query pesa su ogni accesso.

Serve una convenzione **unica** per tutte le liste, decisa una volta: ogni entità nuova la segue
senza ridiscuterla.

## Decisione

### Richiesta

```
GET /api/<entità>?page=0&size=50&sort=nome,asc&q=rossi
```

- `page` da 0, `size` predefinito **50**, massimo **200**
  (`spring.data.web.pageable.max-page-size`: oltre il massimo viene ridotto, senza errore);
- `sort` nel formato di Spring (`campo,asc|desc`), ripetibile; ogni entità dichiara un ordinamento
  predefinito stabile (che include sempre un campo univoco, per non avere pagine instabili);
- `q` è la **ricerca libera** sui campi che l'entità dichiara: confronto `ILIKE` senza distinzione
  di maiuscole, con indice **trigram** (`pg_trgm`) dove la tabella cresce;
- i filtri specifici dell'entità sono parametri espliciti (es. `?stato=aperta`), mai dentro `q`.

### Risposta

Un DTO **esplicito** `PageDto<T>`, mai la serializzazione di `Page` di Spring (il suo formato JSON
non è stabile tra le versioni):

```json
{ "content": [ … ], "page": 0, "size": 50, "totalElements": 1234, "totalPages": 25 }
```

### Database

Ogni lista ha un indice che copre `(tenant_id, <campo di ordinamento predefinito>)`. L'indice su
`tenant_id` da solo non basta quando l'ordinamento è su un altro campo.

### Confine con la sincronizzazione

La paginazione serve alle **liste dell'interfaccia**. La **sincronizzazione offline non la usa**:
quella passa dal cursore incrementale di `pullChanges` (endpoint dedicato, ADR successivo). Sono due
meccanismi distinti e non vanno confusi: una pagina dell'interfaccia non è uno stato replicabile.

## Conseguenze

- `totalElements` costa un `COUNT` a ogni richiesta. A questa scala è accettabile; se diventasse un
  problema si aggiungerà `?count=false` per le liste a scorrimento infinito, senza cambiare il resto.
- La pagina Angular dei clienti va adeguata (tabella con paginatore Material e campo di ricerca) e
  la copia offline non è più «tutto l'elenco»: diventa la copia che il cursore di sincronizzazione
  mantiene.

## Alternative scartate

- **Paginazione a cursore (keyset) per le liste dell'interfaccia**: più efficiente in profondità,
  ma impedisce il salto a una pagina qualsiasi e l'ordinamento libero per colonna, che su una
  tabella da PC è ciò che gli utenti si aspettano. Il cursore si usa dove serve davvero: nella
  sincronizzazione.
- **Ricerca full-text di PostgreSQL (`tsvector`)**: sovradimensionata per cercare un cognome o una
  P.IVA, e peggiore sulle sottostringhe. Candidata in seguito per la ricerca dentro i documenti.
- **Filtrare lato client sulla copia locale**: funziona finché i dati stanno sul dispositivo, cioè
  non con le tabelle che crescono. E l'elenco completo andrebbe comunque scaricato.
