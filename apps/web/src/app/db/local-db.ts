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
export interface LocalCustomer {
  id: string;
  tenantId: string;
  name: string;
  email: string | null;
  updatedAt: string;
}

/** Stato della sincronizzazione, ad esempio il cursore dell'ultimo pull per entità. */
export interface SyncState {
  key: string;
  cursor: string | null;
}

/**
 * Database locale (IndexedDB) per l'uso offline.
 * Ogni modifica allo schema richiede una nuova `version()`: non modificare quelle esistenti.
 */
export class LocalDb extends Dexie {
  outbox!: Table<OutboxEntry, string>;
  customers!: Table<LocalCustomer, string>;
  syncState!: Table<SyncState, string>;

  // Nota: in Dexie si dichiarano solo chiave primaria e indici; i campi non indicizzati
  // (es. OutboxEntry.lastError) si aggiungono senza nuova versione.
  constructor(name = 'gestionale') {
    super(name);
    this.version(1).stores({
      outbox: 'id, createdAt, entity',
      customers: 'id, tenantId, updatedAt',
      syncState: 'key',
    });
  }
}

/** Istanza condivisa del database locale, sostituibile nei test. */
export const LOCAL_DB = new InjectionToken<LocalDb>('LOCAL_DB', {
  providedIn: 'root',
  factory: () => new LocalDb(),
});
