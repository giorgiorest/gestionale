# ADR 0018 — Cancellazione logica, blocco ottimistico e tracciatura delle scritture

- **Stato**: accettata
- **Data**: 2026-11-24

## Contesto

Il prodotto si usa **in cantiere, senza campo**: le scritture avvengono sul dispositivo e vengono
inviate al server quando la connessione torna (outbox, [ADR 0008](0008-privacy-dati-sul-dispositivo.md)).
Questo cambia il significato di «cancellare un record».

1. **Una cancellazione fisica non si può sincronizzare.** Se la riga sparisce dalla tabella, il
   server non ha più modo di dire a un dispositivo rimasto offline «quel cliente non esiste più»:
   il dispositivo, alla riaccensione, non vede differenze e tiene il record per sempre. La
   sincronizzazione incrementale per cursore (voce 1.2 della roadmap) ha bisogno di sapere **cosa è
   stato cancellato**, non solo cosa è stato modificato.
2. **La outbox inviava `DELETE /api/{entità}/{id}` a un endpoint che non esisteva**: una
   cancellazione accodata restava in coda per sempre con un 404 come `lastError`.
3. **Due dispositivi possono modificare lo stesso record** mentre sono scollegati. Senza un numero
   di versione, la seconda scrittura sovrascrive la prima in silenzio e nessuno se ne accorge.
4. **Non si sapeva chi avesse scritto cosa.** Serve al supporto («chi ha cancellato questo
   cliente?») e sarà la base del registro delle modifiche (voce 5.1).

## Decisione

Ogni tabella di dominio ha quattro colonne, aggiunte dalla stessa migrazione che la crea:

| Colonna | Tipo | Significato |
|---------|------|-------------|
| `deleted_at` | `TIMESTAMPTZ` | NULL = record attivo; valorizzato = cancellato (tombstone) |
| `version` | `BIGINT NOT NULL DEFAULT 0` | blocco ottimistico, `@Version` di JPA |
| `created_by` | `TEXT` | claim `sub` di chi ha creato il record |
| `updated_by` | `TEXT` | claim `sub` di chi lo ha modificato o cancellato per ultimo |

1. **La cancellazione è logica.** `DELETE /api/{entità}/{id}` valorizza `deleted_at`: la riga resta
   in tabella come **tombstone**, e sarà quella a propagare la cancellazione ai dispositivi offline.
   Le letture (`GET` di elenco e di dettaglio) escludono i record cancellati.
2. **`DELETE` è idempotente** come il `PUT`: risponde `204 No Content` anche se l'id non esiste, se
   appartiene a un altro tenant (invisibile per la RLS) o se il record era già cancellato. Una voce
   della outbox non deve poter restare bloccata in coda; e la risposta per un id di un altro tenant
   è indistinguibile da quella per un id inesistente, così non rivela niente.
3. **Un `DELETE` ripetuto non cambia nulla**: né `deleted_at`, né `updated_at`, né `version`.
   Altrimenti ogni reinvio dalla outbox rimanderebbe lo stesso tombstone a tutti i dispositivi.
4. **Un `PUT` successivo alla cancellazione riattiva il record** (`deleted_at` torna NULL), mentre
   `created_at` e `created_by` non cambiano. L'id è scelto dal client: un `PUT` è l'ultima volontà
   del client sullo stato di quel record (*last-write-wins*, coerente con la strategia già
   documentata in [docs/architettura.md](../architettura.md)).
5. **`version`** è gestita da Hibernate (`@Version`) e compare nelle risposte dell'API. Un conflitto
   diventa `409` (RFC 9457) tramite `ApiExceptionHandler`. Il controllo lato client con
   `If-Match` e la risoluzione nell'interfaccia sono la voce 1.5 della roadmap: qui si mettono
   solo le fondamenta, perché aggiungere la colonna dopo, su tabelle piene, costa di più.
6. **`created_by`/`updated_by` sono `TEXT`**, come `app_users.user_id`: l'identificativo del
   provider di identità non è necessariamente un UUID, e il codice non deve dipendere da Keycloak
   (ADR 0003). Sono **NULL** per i record scritti da un processo di sistema (per esempio i dati di
   esempio del tenant demo): è un'informazione onesta, non un utente finto.
7. **Nessun vincolo di chiave esterna verso l'utente**: la rubrica `app_users` contiene solo chi ha
   già effettuato un accesso, e un utente può essere rimosso dal provider. Il nome da mostrare si
   risolve dalla rubrica quando c'è, altrimenti si mostra l'identificativo.

8. **Le colonne si aggiungono con una procedura**, `add_sync_columns(regclass)` (in V6), come già si
   fa per la Row Level Security con `enable_tenant_rls`: una tabella nuova fa due `CALL` e non può
   sbagliare tipi o default. Nel codice Java le quattro colonne e la regola di idempotenza di
   `markDeleted` restano per ora dentro `Customer`: con **una** sola entità di dominio la forma
   dell'astrazione non si conosce ancora. Vanno estratte in un `@MappedSuperclass` quando arriva la
   seconda entità (voce 2.1 o 2.2), prima che la terza le ricopi.
9. **Il filtro `deleted_at IS NULL` sta nel nome dei metodi del repository**
   (`findByTenantIdAndDeletedAtIsNullOrderByNameAsc`), non in un `@SQLRestriction` sull'entità.
   Un `@SQLRestriction` nasconderebbe i tombstone *anche* ai due punti che devono vederli — la
   riattivazione nell'upsert e il `DELETE` idempotente — e li costringerebbe a una query nativa per
   aggirare il filtro, cioè a scrivere l'eccezione nel modo più fragile. La voce 1.2
   (`GET /api/sync/changes`) ha lo stesso bisogno: i tombstone sono il suo contenuto. Il prezzo di
   questa scelta è che il filtro va ricordato a ogni query nuova; il nome del metodo è il promemoria.

## Conseguenze

- **Le query di lettura devono filtrare `deleted_at IS NULL`.** È la trappola di questa scelta: una
  query che lo dimentica mostra record cancellati. Per questo i metodi del repository lo portano nel
  nome (`findByTenantIdAndDeletedAtIsNullOrderByNameAsc`) e il ricettario in `CLAUDE.md` lo include
  nel modello da copiare.
- **Gli indici seguono il predicato**: l'elenco diventa «del tenant, non cancellati, ordinati per
  nome», quindi vuole un indice **parziale** `(tenant_id, name) WHERE deleted_at IS NULL`. Senza,
  ogni elenco rilegge e riordina anche tutti i tombstone, che crescono per sempre.
- I vincoli di unicità applicativi (per esempio la P.IVA di un cliente, voce 2.1) devono diventare
  **indici parziali** `WHERE deleted_at IS NULL`: altrimenti un record cancellato impedisce di
  reinserire lo stesso dato.
- Le righe cancellate restano e occupano spazio. Serve una politica di conservazione (voce 5.7,
  GDPR operativo): il tombstone è un dato tecnico e può essere ridotto al solo id quando il periodo
  di sincronizzazione è passato. Finché i volumi sono quelli di un'impresa edile, non è un problema.
- La cancellazione definitiva di dati personali su richiesta dell'interessato (art. 17 GDPR) **non**
  è questa: è una cancellazione fisica, prevista dalla voce 5.7. Questa ADR riguarda la
  cancellazione *operativa* fatta dall'utente.
- Il ripristino del tenant demo continua a cancellare fisicamente (`deleteByTenantId`): è un
  azzeramento, non un'operazione dell'utente.

## Alternative scartate

- **Tabella separata di tombstones** (`deleted_rows(entity, id, deleted_at)`): tiene pulite le
  tabelle di dominio, ma raddoppia i punti da aggiornare a ogni cancellazione, non permette di
  riattivare un record e perde `updated_by`. Vantaggio reale solo con volumi molto maggiori.
- **Cancellazione fisica più `DELETE` che risponde 404**: è la situazione di partenza, e rende la
  sincronizzazione impossibile (punto 1 del contesto).
- **`deleted BOOLEAN`** invece di `deleted_at`: non dice *quando*, che è l'informazione che serve al
  cursore della sincronizzazione.
- **Blocco pessimistico** (`SELECT … FOR UPDATE`): inutilizzabile con un client offline, che non
  tiene una transazione aperta per ore.
- **Auditing automatico di Spring Data** (`@CreatedBy`, `AuditorAware`): nasconde in un bean il
  passaggio dell'utente e vale solo dentro una richiesta HTTP. Il progetto passa l'utente in modo
  esplicito ai servizi, come già fa per le notifiche. Questo **non** esclude un
  `@MappedSuperclass` con le quattro colonne (punto 8): lì l'utente resterebbe un parametro.
- **Filtro globale sull'entità** (`@SQLRestriction`, o un `@FilterDef` attivo per default): vedi il
  punto 9. Rende invisibili i tombstone anche a chi deve leggerli.
