import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../api/api-url';
import { LOCAL_DB, LocalDb, OutboxEntry } from '../../db/local-db';
import { SyncService } from './sync.service';

/** Outbox in memoria: jsdom non ha IndexedDB. */
function fakeDb(entries: OutboxEntry[]): LocalDb {
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
    },
  } as unknown as LocalDb;
}

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('SyncService', () => {
  let entries: OutboxEntry[];
  let service: SyncService;
  let http: HttpTestingController;

  beforeEach(() => {
    entries = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(entries) },
      ],
    });
    service = TestBed.inject(SyncService);
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
});
