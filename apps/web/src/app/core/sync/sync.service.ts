import { Injectable, inject } from '@angular/core';
import { LOCAL_DB, OutboxEntry, OutboxOperation } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/**
 * Sincronizzazione offline (stub).
 *
 * - Le scritture dei componenti passano da `enqueue()` e finiscono nella outbox locale.
 * - `flush()` invierà la outbox al server in ordine, con retry.
 * - `pullChanges()` scaricherà le modifiche remote a partire dall'ultimo cursore.
 */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly db = inject(LOCAL_DB);

  /** Registra una modifica locale nella outbox e restituisce la voce creata. */
  async enqueue(entity: string, operation: OutboxOperation, payload: unknown): Promise<OutboxEntry> {
    const entry: OutboxEntry = {
      id: uuidv7(),
      entity,
      operation,
      payload,
      createdAt: new Date().toISOString(),
      attempts: 0,
    };
    await this.db.outbox.add(entry);
    return entry;
  }

  /** Invia al server le modifiche in outbox. Stub: non invia nulla, restituisce le voci inviate. */
  async flush(): Promise<number> {
    // TODO: POST in ordine di createdAt, rimozione delle voci confermate, incremento di attempts sugli errori.
    return 0;
  }

  /** Scarica le modifiche remote dopo l'ultimo cursore. Stub: restituisce le modifiche applicate. */
  async pullChanges(): Promise<number> {
    // TODO: GET /api/sync/changes?cursor=…, applicazione al db locale, salvataggio del nuovo cursore.
    return 0;
  }
}
