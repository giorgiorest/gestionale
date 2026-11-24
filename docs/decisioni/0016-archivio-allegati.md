# ADR 0016 — Allegati: archivio compatibile S3, URL firmati, mai byte attraverso l'API

- **Stato**: accettata
- **Data**: 2026-11-24

## Contesto

Nel verticale scelto ([ADR 0012](0012-verticale-edilizia.md)) gli allegati non sono un accessorio:
**le foto di cantiere scattate dal telefono** sono parte del rapportino, insieme a PDF di
preventivi firmati, DDT dei fornitori e documenti di commessa. Vanno raccolti anche **senza
connessione** e inviati al ritorno del campo.

Oggi il prodotto non ha alcun archivio di file. Il database non è il posto giusto: i file crescono,
appesantiscono i backup (`pg_dumpall`) e saturano il disco della macchina.

## Decisione

1. **L'unico accoppiamento è l'API compatibile S3**, usata con l'**AWS SDK v2 per Java**
   (Apache-2.0). Nessun SDK proprietario di un fornitore: l'archivio deve restare sostituibile,
   per la stessa ragione per cui il provider di identità è sostituibile ([ADR 0003](0003-identita-keycloak.md)).
2. **Il fornitore si configura per installazione** (endpoint, bucket, regione, chiavi da variabili
   d'ambiente). Per le installazioni in SaaS la preferenza è un fornitore **europeo** (dati personali,
   GDPR). Per un'installazione in sede, qualunque archivio compatibile S3 presente dal cliente.
3. **In sviluppo locale**: un container MinIO in `infra/docker/compose.yml`. Solo sviluppo: il
   server MinIO è AGPL-3.0 e non va distribuito dentro il prodotto.
4. **I byte non passano mai dall'API.** L'API controlla permesso, entitlement e tenant, poi
   restituisce un **URL firmato** di breve durata (ordine di minuti): `PUT` per il caricamento,
   `GET` per il download. L'API non fa da proxy: non regge le foto di 50 utenti e non deve.
5. **Struttura della chiave**: `<tenant_id>/<entità>/<id-entità>/<id-allegato>`. Il `tenant_id` è il
   primo segmento, così la separazione tra aziende è visibile anche nell'archivio e si può rinforzare
   con una policy sul prefisso.
6. **Tabella `attachments`** con `tenant_id NOT NULL` e RLS (regola di progetto): nome originale,
   tipo MIME, dimensione, `sha256`, chi ha caricato, quando, entità e id a cui è appeso, chiave
   nell'archivio. L'id è un UUID v7 generato dal client, come tutte le entità.
7. **Controlli al caricamento**: dimensione massima per file e **elenco dei tipi MIME ammessi**
   (immagini e PDF per cominciare). Il tipo dichiarato dal client non è attendibile: va verificato
   lato server alla conferma dei metadati.
8. **Offline**: la foto scattata senza campo resta sul dispositivo e la outbox registra un
   caricamento in attesa; al ritorno della connessione si carica il file e poi si confermano i
   metadati. I metadati si sincronizzano come ogni altra entità; **i file si scaricano su richiesta**
   e si tengono in cache in modo selettivo, non tutto l'archivio.
9. La cache locale dei file è un dato personale sul dispositivo: si svuota all'uscita come il resto
   ([ADR 0008](0008-privacy-dati-sul-dispositivo.md)), e va aggiunta a `LocalDataService.clear()`.
10. Ogni nuova archiviazione sul dispositivo va documentata nella pagina `/privacy` e in
    [docs/privacy-gdpr.md](../privacy-gdpr.md); l'archivio esterno va aggiunto al registro dei
    trattamenti e coperto da un contratto con il fornitore.

## Conseguenze

- Il backup non è più solo il database: **l'archivio dei file va nel piano di backup**, e il piano
  va provato con un ripristino reale.
- Servono nuove variabili di produzione: vanno aggiunte a `infra/deploy/oracle/.env.example`,
  a `compose.yml` e alla guida di installazione (regola di progetto).
- Un URL firmato è un permesso già concesso: la durata deve restare breve e l'URL non va messo nei
  log né nelle notifiche email.

## Punti noti ancora aperti

- **Antivirus** sui file caricati (ClamAV o scansione del fornitore): non incluso ora, perché
  aggiunge un servizio da gestire. Va documentato come limite e affrontato prima di aprire il
  caricamento a utenti poco fidati.
- **Cancellazione**: un allegato cancellato va rimosso anche dall'archivio, con un processo che
  ripulisce gli oggetti orfani (lavora per tutti i tenant: serve `WorkerContext` e una policy RLS
  dedicata, come per l'invio email).

## Alternative scartate

- **File nel database** (`bytea` o Large Object): semplice e transazionale, ma gonfia i backup, il
  disco e la memoria del server. Con le foto da cantiere è la scelta che si paga subito.
- **File sul disco della macchina**: non sopravvive alla sostituzione del server, non si replica,
  e non c'è modo di firmare un URL senza far passare i byte dall'applicazione.
- **SDK del singolo fornitore**: lega il prodotto a quel fornitore in ogni installazione.
- **MinIO distribuito nel prodotto**: server AGPL-3.0 dentro ciò che si rivende: da evitare.
