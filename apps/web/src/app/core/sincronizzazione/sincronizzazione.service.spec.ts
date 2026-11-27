import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../api/api-url';
import { ProfiloService } from '../profilo/profilo.service';
import { LOCAL_DB, ClienteLocale, DbLocale, OutboxEntry, StatoSincronizzazione } from '../../db/db-locale';
import { SincronizzazioneService } from './sincronizzazione.service';

const TENANT = 'tenant-1';

/** Outbox, clienti e stato della sincronizzazione in memoria: jsdom non ha IndexedDB. */
function fakeDb(entries: OutboxEntry[], customers: ClienteLocale[], syncState: StatoSincronizzazione[]): DbLocale {
  return {
    outbox: {
      add: async (entry: OutboxEntry) => {
        entries.push(entry);
        return entry.id;
      },
      count: async () => entries.length,
      delete: async (id: string) => {
        entries.splice(entries.findIndex((e) => e.id === id), 1);
      },
      update: async (id: string, changes: Partial<OutboxEntry>) => {
        Object.assign(entries.find((e) => e.id === id)!, changes);
        return 1;
      },
      orderBy: () => ({ toArray: async () => [...entries].sort((a, b) => a.createdAt.localeCompare(b.createdAt)) }),
      where: (field: 'entity') => ({
        equals: (value: string) => ({
          sortBy: async (key: 'createdAt') =>
            entries
              .filter((e) => e[field] === value)
              .sort((a, b) => a[key].localeCompare(b[key])),
        }),
      }),
    },
    customers: {
      bulkPut: async (rows: ClienteLocale[]) => {
        for (const row of rows) {
          const i = customers.findIndex((c) => c.id === row.id);
          if (i >= 0) customers[i] = row;
          else customers.push(row);
        }
      },
      bulkDelete: async (ids: string[]) => {
        for (const id of ids) {
          const i = customers.findIndex((c) => c.id === id);
          if (i >= 0) customers.splice(i, 1);
        }
      },
    },
    syncState: {
      get: async (key: string) => syncState.find((s) => s.key === key),
      put: async (row: StatoSincronizzazione) => {
        const i = syncState.findIndex((s) => s.key === row.key);
        if (i >= 0) syncState[i] = row;
        else syncState.push(row);
        return row.key;
      },
    },
  } as unknown as DbLocale;
}

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('SyncService', () => {
  let entries: OutboxEntry[];
  let customers: ClienteLocale[];
  let syncState: StatoSincronizzazione[];
  let service: SincronizzazioneService;
  let http: HttpTestingController;

  beforeEach(() => {
    entries = [];
    customers = [];
    syncState = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(entries, customers, syncState) },
        { provide: ProfiloService, useValue: { load: async () => ({ tenant: { id: TENANT } }) } },
      ],
    });
    service = TestBed.inject(SincronizzazioneService);
    http = TestBed.inject(HttpTestingController);
  });

  it('enqueue registra la modifica nella outbox', async () => {
    const entry = await service.enqueue('customers', 'create', { id: 'c1', name: 'Rossi' });

    expect(entries).toEqual([entry]);
    expect(entry.operation).toBe('create');
    expect(entry.attempts).toBe(0);
    expect(entry.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(service.pending()).toBe(1);
  });

  it('flush invia in ordine con PUT idempotente e svuota la outbox', async () => {
    await service.enqueue('customers', 'create', { id: 'c1', name: 'Rossi' });
    await service.enqueue('customers', 'update', { id: 'c1', name: 'Rossi S.p.A.' });

    const done = service.flush();
    await tick();
    const first = http.expectOne('/api/customers/c1');
    expect(first.request.method).toBe('PUT');
    expect(first.request.body).toEqual({ name: 'Rossi' });
    first.flush({});
    await tick();
    const second = http.expectOne('/api/customers/c1');
    expect(second.request.body).toEqual({ name: 'Rossi S.p.A.' });
    second.flush({});

    expect(await done).toBe(2);
    expect(entries).toEqual([]);
    expect(service.pending()).toBe(0);
  });

  it('si ferma sugli errori di rete e conserva la voce', async () => {
    await service.enqueue('customers', 'create', { id: 'c1', name: 'Rossi' });
    await service.enqueue('customers', 'create', { id: 'c2', name: 'Bianchi' });

    const done = service.flush();
    await tick();
    http.expectOne('/api/customers/c1').error(new ProgressEvent('error'));

    expect(await done).toBe(0);
    http.expectNone('/api/customers/c2');
    expect(entries.map((e) => e.attempts)).toEqual([1, 0]);
    expect(entries[0].lastError).toBeUndefined();
  });

  it('segna gli errori definitivi e prosegue con le voci successive', async () => {
    await service.enqueue('customers', 'create', { id: 'c1', name: '' });
    await service.enqueue('customers', 'create', { id: 'c2', name: 'Bianchi' });

    const done = service.flush();
    await tick();
    http.expectOne('/api/customers/c1').flush({}, { status: 400, statusText: 'Bad Request' });
    await tick();
    http.expectOne('/api/customers/c2').flush({});

    expect(await done).toBe(1);
    expect(entries).toHaveLength(1);
    expect(entries[0].lastError).toBe('HTTP 400');
  });

  it('pendingByEntity restituisce l\'ultima operazione in coda per ogni id', async () => {
    await service.enqueue('customers', 'create', { id: 'c1', name: 'Rossi' });
    await service.enqueue('notifications', 'update', { id: 'n1' });
    await service.enqueue('customers', 'delete', { id: 'c1' });

    const pending = await service.pendingByEntity('customers');

    // Solo l'entità chiesta, e per c1 vale la cancellazione accodata per ultima.
    expect([...pending]).toEqual([['c1', 'delete']]);
  });

  it('pullChanges applica le modifiche ricevute e salva il cursore', async () => {
    const done = service.pullChanges();
    await tick();
    http.expectOne((r) => r.url === '/api/sync/changes').flush({
      cursor: 'cursore-1',
      hasMore: false,
      entities: {
        customers: [
          { id: 'c1', updatedAt: '2026-01-01T00:00:00Z', version: 0, deleted: false, data: { name: 'Rossi', email: null, version: 0, updatedAt: '2026-01-01T00:00:00Z' } },
          { id: 'c2', updatedAt: '2026-01-01T00:00:00Z', version: 1, deleted: true, data: null },
        ],
      },
    });

    expect(await done).toBe(2);
    expect(customers).toEqual([{ id: 'c1', tenantId: TENANT, name: 'Rossi', email: null, version: 0, updatedAt: '2026-01-01T00:00:00Z' }]);
    expect(syncState).toEqual([{ key: 'pull', cursor: 'cursore-1' }]);
  });

  it('pullChanges richiama finché hasMore è vero, passando il cursore ricevuto', async () => {
    const done = service.pullChanges();
    await tick();
    http.expectOne((r) => r.url === '/api/sync/changes' && r.params.get('cursor') === null).flush({
      cursor: 'cursore-1',
      hasMore: true,
      entities: { customers: [{ id: 'c1', updatedAt: '', version: 0, deleted: false, data: { name: 'Rossi', email: null } }] },
    });
    await tick();
    http.expectOne((r) => r.url === '/api/sync/changes' && r.params.get('cursor') === 'cursore-1').flush({
      cursor: 'cursore-2',
      hasMore: false,
      entities: { customers: [{ id: 'c2', updatedAt: '', version: 0, deleted: false, data: { name: 'Bianchi', email: null } }] },
    });

    expect(await done).toBe(2);
    expect(customers.map((c) => c.id).sort()).toEqual(['c1', 'c2']);
  });

  it('pullChanges non sovrascrive un record con una modifica locale ancora in coda', async () => {
    await service.enqueue('customers', 'update', { id: 'c1', name: 'Modificato in locale' });
    const done = service.pullChanges();
    await tick();
    http.expectOne((r) => r.url === '/api/sync/changes').flush({
      cursor: 'cursore-1',
      hasMore: false,
      entities: { customers: [{ id: 'c1', updatedAt: '', version: 0, deleted: false, data: { name: 'Dal server', email: null } }] },
    });

    expect(await done).toBe(0);
    expect(customers).toEqual([]);
  });

  it('pullChanges ignora le entità senza una tabella locale', async () => {
    const done = service.pullChanges();
    await tick();
    http.expectOne((r) => r.url === '/api/sync/changes').flush({
      cursor: 'cursore-1',
      hasMore: false,
      entities: { daily_report_materials: [{ id: 'm1', updatedAt: '', version: 0, deleted: false, data: {} }] },
    });

    expect(await done).toBe(0);
    expect(syncState).toEqual([{ key: 'pull', cursor: 'cursore-1' }]);
  });
});
