import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Table } from 'dexie';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../api/api-url';
import { ProfileService } from '../profile/profile.service';
import { LOCAL_DB, LocalDb, OutboxEntry, OutboxOperation } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

/** Payload di una modifica: contiene sempre l'id (UUID generato dal client) dell'entità. */
export type OutboxPayload = { id: string } & Record<string, unknown>;

/** Una riga cambiata di un'entità, come la restituisce `GET /api/sync/changes` (SyncChangeDto). */
interface SyncChangeApiDto {
  id: string;
  updatedAt: string;
  version: number;
  deleted: boolean;
  data: Record<string, unknown> | null;
}

/** Risposta di `GET /api/sync/changes` (SyncChangesDto): il cursore è unico e opaco. */
interface SyncChangesResponse {
  cursor: string;
  hasMore: boolean;
  entities: Record<string, SyncChangeApiDto[]>;
}

/** Riga locale minima: le tabelle Dexie delle entità sincronizzate hanno sempre un `id` stringa. */
type LocalRow = Record<string, unknown> & { id: string };

/** Chiave in `syncState` del cursore di `pullChanges()` (un solo cursore: il backend copre già tutte le entità). */
const PULL_CURSOR_KEY = 'pull';

/** Intervallo del pull periodico in background. */
const PULL_INTERVAL_MS = 120_000;

/** Entità esposte da `GET /api/sync/changes` che hanno una tabella Dexie locale (ADR: vedi local-db.ts). */
function localTable(db: LocalDb, entity: string): Table<LocalRow, string> | null {
  switch (entity) {
    case 'customers':
      return db.customers as unknown as Table<LocalRow, string>;
    case 'suppliers':
      return db.suppliers as unknown as Table<LocalRow, string>;
    case 'resources':
      return db.resources as unknown as Table<LocalRow, string>;
    case 'commesse':
      return db.commesse as unknown as Table<LocalRow, string>;
    case 'resource_assignments':
      return db.resourceAssignments as unknown as Table<LocalRow, string>;
    case 'work_activities':
      return db.workActivities as unknown as Table<LocalRow, string>;
    case 'daily_reports':
      return db.dailyReports as unknown as Table<LocalRow, string>;
    case 'work_entries':
      return db.workEntries as unknown as Table<LocalRow, string>;
    default:
      // Es. "daily_report_materials": niente tabella locale finché non esiste la UI che la usa
      // (roadmap 3.3). Si ignora l'entità senza bloccare le altre.
      return null;
  }
}

/**
 * Sincronizzazione offline.
 *
 * - Le scritture dei componenti passano da `enqueue()` e finiscono nella outbox locale.
 * - `flush()` invia la outbox al server in ordine di creazione:
 *   `create`/`update` → `PUT {api}/{entity}/{id}` (idempotente), `delete` → `DELETE {api}/{entity}/{id}`.
 *   Errori di rete, 5xx, 401, 408 e 429 fermano l'invio (si riprova dopo); gli altri 4xx sono
 *   errori definitivi: la voce resta in coda con `lastError` e l'invio prosegue con le successive.
 * - `pullChanges()` scarica le modifiche remote da `GET /api/sync/changes`, a partire dal cursore
 *   salvato in `syncState`, e le applica alle tabelle Dexie locali (tombstone → `bulkDelete`, resto
 *   → `bulkPut`). Un id ancora in coda nella outbox non viene toccato: vince la modifica locale non
 *   ancora inviata (stessa idea di `pendingByEntity()` già usata da `CustomersService.load()`).
 */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly db = inject(LOCAL_DB);
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly profile = inject(ProfileService);

  private flushing: Promise<number> | null = null;
  private pulling: Promise<number> | null = null;
  private pullTimer: ReturnType<typeof setInterval> | null = null;

  /** Voci in attesa di invio. */
  readonly pending = signal(0);

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => {
        void this.flush();
        void this.pullChanges();
      });
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

  /**
   * Modifiche di un'entità ancora in coda: id → operazione accodata per **ultima**.
   * Serve a chi ricarica un elenco dal server per non rimettere in locale un record la cui
   * cancellazione non è ancora stata inviata (e per non buttare un record non ancora creato).
   */
  async pendingByEntity(entity: string): Promise<Map<string, OutboxOperation>> {
    const entries = await this.db.outbox.where('entity').equals(entity).sortBy('createdAt');
    return new Map(entries.map((e) => [(e.payload as OutboxPayload).id, e.operation]));
  }

  /**
   * Scarica le modifiche remote dopo l'ultimo cursore e le applica alle tabelle locali.
   * Richiama l'endpoint finché `hasMore` è vero, salvando il cursore dopo ogni pagina: un errore
   * a metà strada lascia comunque il progresso fatto finora. Restituisce le modifiche applicate.
   */
  pullChanges(): Promise<number> {
    this.pulling ??= this.doPull().finally(() => (this.pulling = null));
    return this.pulling;
  }

  private async doPull(): Promise<number> {
    const tenantId = (await this.profile.load())?.tenant?.id ?? '';
    let cursor = (await this.db.syncState.get(PULL_CURSOR_KEY))?.cursor ?? undefined;
    let applied = 0;
    for (;;) {
      let page: SyncChangesResponse;
      try {
        page = await firstValueFrom(
          this.http.get<SyncChangesResponse>(`${this.apiUrl}/sync/changes`, cursor ? { params: { cursor } } : {}),
        );
      } catch {
        break; // Offline o server irraggiungibile: si riprova al prossimo pull.
      }
      applied += await this.applyChanges(page.entities, tenantId);
      cursor = page.cursor;
      await this.db.syncState.put({ key: PULL_CURSOR_KEY, cursor });
      if (!page.hasMore) {
        break;
      }
    }
    return applied;
  }

  /** Avvia il pull periodico in background (idempotente). */
  startPullPolling(): void {
    if (this.pullTimer) {
      return;
    }
    void this.pullChanges();
    this.pullTimer = setInterval(() => {
      if (document.visibilityState === 'visible') {
        void this.pullChanges();
      }
    }, PULL_INTERVAL_MS);
  }

  private async applyChanges(entities: Record<string, SyncChangeApiDto[]>, tenantId: string): Promise<number> {
    let applied = 0;
    for (const [entity, changes] of Object.entries(entities)) {
      if (changes.length === 0) {
        continue;
      }
      const table = localTable(this.db, entity);
      if (!table) {
        console.warn(`pullChanges: nessuna tabella locale per l'entità "${entity}", modifiche ignorate.`);
        continue;
      }
      const pending = await this.pendingByEntity(entity);
      const toUpsert: LocalRow[] = [];
      const toDelete: string[] = [];
      for (const change of changes) {
        if (pending.has(change.id)) {
          continue; // Una modifica locale non ancora inviata ha la precedenza.
        }
        if (change.deleted) {
          toDelete.push(change.id);
        } else {
          toUpsert.push({ ...(change.data as Record<string, unknown>), id: change.id, tenantId });
        }
        applied++;
      }
      await Promise.all([
        toUpsert.length ? table.bulkPut(toUpsert) : Promise.resolve(),
        toDelete.length ? table.bulkDelete(toDelete) : Promise.resolve(),
      ]);
    }
    return applied;
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
