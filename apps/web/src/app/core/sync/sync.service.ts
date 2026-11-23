import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../api/api-url';
import { LOCAL_DB, OutboxEntry, OutboxOperation } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/** Payload di una modifica: contiene sempre l'id (UUID generato dal client) dell'entità. */
export type OutboxPayload = { id: string } & Record<string, unknown>;

/**
 * Sincronizzazione offline.
 *
 * - Le scritture dei componenti passano da `enqueue()` e finiscono nella outbox locale.
 * - `flush()` invia la outbox al server in ordine di creazione:
 *   `create`/`update` → `PUT {api}/{entity}/{id}` (idempotente), `delete` → `DELETE {api}/{entity}/{id}`.
 *   Errori di rete, 5xx, 401, 408 e 429 fermano l'invio (si riprova dopo); gli altri 4xx sono
 *   errori definitivi: la voce resta in coda con `lastError` e l'invio prosegue con le successive.
 * - `pullChanges()` scaricherà le modifiche remote a partire dall'ultimo cursore (da implementare).
 */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly db = inject(LOCAL_DB);
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);

  private flushing: Promise<number> | null = null;

  /** Voci in attesa di invio. */
  readonly pending = signal(0);

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => void this.flush());
    }
  }

  /** Registra una modifica locale nella outbox e restituisce la voce creata. */
  async enqueue(entity: string, operation: OutboxOperation, payload: OutboxPayload): Promise<OutboxEntry> {
    const entry: OutboxEntry = {
      id: uuidv7(),
      entity,
      operation,
      payload,
      createdAt: new Date().toISOString(),
      attempts: 0,
    };
    await this.db.outbox.add(entry);
    await this.refreshPending();
    return entry;
  }

  /** Invia al server le modifiche in outbox. Restituisce le voci inviate con successo. */
  flush(): Promise<number> {
    this.flushing ??= this.doFlush().finally(() => (this.flushing = null));
    return this.flushing;
  }

  /** Scarica le modifiche remote dopo l'ultimo cursore. Stub: restituisce le modifiche applicate. */
  async pullChanges(): Promise<number> {
    // TODO: GET /api/sync/changes?cursor=…, applicazione al db locale, salvataggio del nuovo cursore.
    return 0;
  }

  async refreshPending(): Promise<void> {
    this.pending.set(await this.db.outbox.count());
  }

  private async doFlush(): Promise<number> {
    let sent = 0;
    const entries = await this.db.outbox.orderBy('createdAt').toArray();
    for (const entry of entries) {
      try {
        await this.send(entry);
        await this.db.outbox.delete(entry.id);
        sent++;
      } catch (error) {
        const retryLater = isTransient(error);
        await this.db.outbox.update(entry.id, {
          attempts: entry.attempts + 1,
          lastError: retryLater ? entry.lastError : describe(error),
        });
        if (retryLater) {
          break;
        }
      }
    }
    await this.refreshPending();
    return sent;
  }

  private send(entry: OutboxEntry): Promise<unknown> {
    const { id, ...body } = entry.payload as OutboxPayload;
    const url = `${this.apiUrl}/${entry.entity}/${encodeURIComponent(id)}`;
    return firstValueFrom(entry.operation === 'delete' ? this.http.delete(url) : this.http.put(url, body));
  }
}

function isTransient(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) {
    return true;
  }
  return error.status === 0 || error.status >= 500 || [401, 408, 429].includes(error.status);
}

function describe(error: unknown): string {
  return error instanceof HttpErrorResponse ? `HTTP ${error.status}` : String(error);
}
