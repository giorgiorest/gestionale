import { InjectionToken } from '@angular/core';
import Dexie, { Table } from 'dexie';

/** Tipo di modifica registrata nella outbox. */
export type OutboxOperation = 'create' | 'update' | 'delete';

/** Modifica locale in attesa di essere inviata al server. */
export interface OutboxEntry {
  /** UUID v7 generato dal client. */
  id: string;
  /** Nome dell'entità (es. "customers"). */
  entity: string;
  operation: OutboxOperation;
  payload: unknown;
  /** Timestamp ISO 8601 della modifica. */
  createdAt: string;
  /** Tentativi di invio falliti. */
  attempts: number;
  /** Ultimo errore definitivo del server (es. dati non validi): la voce resta in coda da correggere. */
  lastError?: string;
}

/** Copia locale di un cliente (stesso schema dell'API). */
export interface ClienteLocale {
  id: string;
  tenantId: string;
  name: string;
  email: string | null;
  partitaIva: string | null;
  codiceFiscale: string | null;
  indirizzo: string | null;
  pec: string | null;
  codiceSdi: string | null;
  condizioniPagamento: string | null;
  updatedAt: string;
}

/** Copia locale di un fornitore (stesso schema dell'API, tabella propria: roadmap 2.2). */
export interface FornitoreLocale {
  id: string;
  tenantId: string;
  name: string;
  email: string | null;
  partitaIva: string | null;
  codiceFiscale: string | null;
  indirizzo: string | null;
  pec: string | null;
  codiceSdi: string | null;
  condizioniPagamento: string | null;
  updatedAt: string;
}

/** Copia locale di una risorsa (stesso schema dell'API). */
export interface RisorsaLocale {
  id: string;
  tenantId: string;
  firstName: string;
  lastName: string;
  userId: string | null;
  teamLeadId: string | null;
  updatedAt: string;
}

/** Copia locale di una commessa (stesso schema dell'API). */
export interface CommessaLocale {
  id: string;
  tenantId: string;
  customerId: string;
  nome: string;
  indirizzo: string | null;
  stato: string;
  updatedAt: string;
}

/** Copia locale di un'assegnazione di una risorsa a una commessa (stesso schema dell'API). */
export interface AssegnazioneRisorsaLocale {
  id: string;
  tenantId: string;
  resourceId: string;
  commessaId: string;
  validFrom: string;
  validTo: string | null;
  role: string;
  hourlyCostOverride: number | null;
  active: boolean;
  updatedAt: string;
}

/** Copia locale di una lavorazione di una commessa (stesso schema dell'API). */
export interface LavorazioneLocale {
  id: string;
  tenantId: string;
  commessaId: string;
  parentId: string | null;
  code: string;
  name: string;
  description: string | null;
  status: string;
  progressPercentage: number;
  updatedAt: string;
}

/** Copia locale della testata di un rapportino giornaliero (stesso schema dell'API). */
export interface RapportinoLocale {
  id: string;
  tenantId: string;
  commessaId: string;
  date: string;
  weather: string | null;
  notes: string | null;
  status: string;
  submittedAt: string | null;
  closedAt: string | null;
  updatedAt: string;
}

/** Copia locale di una riga di ore di un rapportino (stesso schema dell'API). */
export interface RigaOreLocale {
  id: string;
  tenantId: string;
  dailyReportId: string;
  resourceId: string;
  workActivityId: string;
  hours: number;
  overtimeHours: number;
  notes: string | null;
  updatedAt: string;
}

/** Copia locale dei metadati di un allegato (stesso schema dell'API; i byte stanno in `FileInCache`). */
export interface AllegatoLocale {
  id: string;
  tenantId: string;
  entityType: string;
  entityId: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  /** null finché il caricamento non è confermato dal server. */
  sha256: string | null;
  updatedAt: string;
}

/** File scelto o scattato che aspetta di essere caricato sull'archivio (roadmap 2.5, ADR 0016). */
export interface CaricamentoInAttesa {
  id: string;
  entityType: string;
  entityId: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  sha256: string;
  blob: Blob;
  createdAt: string;
  attempts: number;
  /** Ultimo errore definitivo del server (es. tipo non ammesso): il file resta qui, da rimuovere. */
  lastError?: string;
}

/** Byte di un allegato già caricato, tenuti sul dispositivo: cache selettiva a spazio limitato. */
export interface FileInCache {
  id: string;
  blob: Blob;
  sizeBytes: number;
  /** Ultimo utilizzo (ms epoch): chi è stato usato meno di recente esce per primo. */
  lastAccess: number;
}

/** Copia locale di una notifica dell'utente (stesso schema di GET /api/notifications). */
export interface NotificaLocale {
  id: string;
  kind: 'system' | 'announcement';
  type: string;
  title: string;
  body: string;
  link: string | null;
  senderName: string | null;
  createdAt: string;
  requiresAck: boolean;
  readAt: string | null;
  acknowledgedAt: string | null;
}

/** Stato della sincronizzazione, ad esempio il cursore dell'ultimo pull per entità. */
export interface StatoSincronizzazione {
  key: string;
  cursor: string | null;
}

/** Informazioni sul database locale stesso (es. a chi appartengono i dati). */
export interface MetaLocale {
  key: string;
  value: string;
}

/**
 * Database locale (IndexedDB) per l'uso offline.
 * Contiene dati personali: appartiene a un solo utente (`meta.owner`) e si svuota all'uscita
 * (vedi DatiLocaliService).
 * Ogni modifica allo schema richiede una nuova `version()`: non modificare quelle esistenti.
 */
export class DbLocale extends Dexie {
  outbox!: Table<OutboxEntry, string>;
  customers!: Table<ClienteLocale, string>;
  suppliers!: Table<FornitoreLocale, string>;
  notifications!: Table<NotificaLocale, string>;
  syncState!: Table<StatoSincronizzazione, string>;
  meta!: Table<MetaLocale, string>;
  resources!: Table<RisorsaLocale, string>;
  commesse!: Table<CommessaLocale, string>;
  resourceAssignments!: Table<AssegnazioneRisorsaLocale, string>;
  workActivities!: Table<LavorazioneLocale, string>;
  dailyReports!: Table<RapportinoLocale, string>;
  workEntries!: Table<RigaOreLocale, string>;
  attachments!: Table<AllegatoLocale, string>;
  pendingUploads!: Table<CaricamentoInAttesa, string>;
  fileCache!: Table<FileInCache, string>;

  // Nota: in Dexie si dichiarano solo chiave primaria e indici; i campi non indicizzati
  // (es. OutboxEntry.lastError) si aggiungono senza nuova versione.
  constructor(name = 'gestionale') {
    super(name);
    this.version(1).stores({
      outbox: 'id, createdAt, entity',
      customers: 'id, tenantId, updatedAt',
      syncState: 'key',
    });
    // v2: notifiche dell'utente.
    this.version(2).stores({
      notifications: 'id, createdAt',
    });
    // v3: proprietario dei dati locali.
    this.version(3).stores({
      meta: 'key',
    });
    // v4: risorse (roadmap.md 2.3).
    this.version(4).stores({
      resources: 'id, tenantId, teamLeadId, updatedAt',
    });
    // v5: commesse (roadmap 3.1).
    this.version(5).stores({
      commesse: 'id, tenantId, customerId, updatedAt',
    });
    // v6: assegnazioni risorsa-commessa (GitHub #47).
    this.version(6).stores({
      resourceAssignments: 'id, tenantId, commessaId, resourceId, updatedAt',
    });
    // v7: lavorazioni (GitHub #48).
    this.version(7).stores({
      workActivities: 'id, tenantId, commessaId, parentId, updatedAt',
    });
    // v8: testata del rapportino giornaliero (GitHub #49).
    this.version(8).stores({
      dailyReports: 'id, tenantId, commessaId, date, updatedAt',
    });
    // v9: righe di ore del rapportino (GitHub #50).
    this.version(9).stores({
      workEntries: 'id, tenantId, dailyReportId, resourceId, workActivityId, updatedAt',
    });
    // v10: fornitori (roadmap 2.2), tabella propria, non condivisa con customers.
    this.version(10).stores({
      suppliers: 'id, tenantId, updatedAt',
    });
    // v11: allegati (roadmap 2.5): metadati, caricamenti in attesa e cache dei file.
    this.version(11).stores({
      attachments: 'id, tenantId, [entityType+entityId], updatedAt',
      pendingUploads: 'id, [entityType+entityId], createdAt',
      fileCache: 'id, lastAccess',
    });
  }
}

/** Istanza condivisa del database locale, sostituibile nei test. */
export const LOCAL_DB = new InjectionToken<DbLocale>('LOCAL_DB', {
  providedIn: 'root',
  factory: () => new DbLocale(),
});
