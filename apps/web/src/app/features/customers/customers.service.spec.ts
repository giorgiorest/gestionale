import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalCustomer, LocalDb, OutboxOperation } from '../../db/local-db';
import { CustomersService } from './customers.service';

const TENANT = 'tenant-1';

function fakeDb(customers: LocalCustomer[]): LocalDb {
  const removeById = (id: string) =>
    customers.splice(
      customers.findIndex((x) => x.id === id),
      1,
    );
  return {
    customers: {
      toArray: async () => [...customers],
      put: async (c: LocalCustomer) => {
        customers.push(c);
        return c.id;
      },
      bulkPut: async (list: LocalCustomer[]) => {
        for (const c of list) {
          const i = customers.findIndex((x) => x.id === c.id);
          if (i >= 0) customers[i] = c;
          else customers.push(c);
        }
      },
      bulkDelete: async (ids: string[]) => ids.forEach(removeById),
      delete: async (id: string) => removeById(id),
    },
  } as unknown as LocalDb;
}

describe('CustomersService', () => {
  let customers: LocalCustomer[];
  /** Coda finta: id → operazione, come la restituisce SyncService.pendingByEntity(). */
  let pending: Map<string, OutboxOperation>;
  let enqueued: unknown[];
  let service: CustomersService;
  let http: HttpTestingController;

  beforeEach(() => {
    customers = [];
    pending = new Map();
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(customers) },
        { provide: ProfileService, useValue: { load: async () => ({ tenant: { id: TENANT } }) } },
        {
          provide: SyncService,
          useValue: {
            flush: async () => 0,
            pendingByEntity: async () => pending,
            enqueue: async (...args: unknown[]) => {
              enqueued.push(args);
              return {};
            },
          },
        },
      ],
    });
    service = TestBed.inject(CustomersService);
    http = TestBed.inject(HttpTestingController);
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  it('carica dal server, salva la copia locale e ordina per nome', async () => {
    customers.push({ id: 'old', tenantId: TENANT, name: 'Rimosso dal server', email: null, updatedAt: '' });
    const done = service.load();
    await tick();
    http.expectOne('/api/customers').flush([
      { id: 'b', name: 'Verdi', email: null, version: 0, updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'a', name: 'Bianchi', email: 'b@example.com', version: 1, updatedAt: '2026-01-01T00:00:00Z' },
    ]);
    await done;

    expect(service.offline()).toBe(false);
    expect(service.customers().map((c) => c.name)).toEqual(['Bianchi', 'Verdi']);
    expect(customers.map((c) => c.id).sort()).toEqual(['a', 'b']);
  });

  it('se il server non risponde usa la copia locale', async () => {
    customers.push({ id: 'a', tenantId: TENANT, name: 'Locale', email: null, updatedAt: '' });
    const done = service.load();
    await tick();
    http.expectOne('/api/customers').error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.customers().map((c) => c.name)).toEqual(['Locale']);
  });

  it('crea il cliente in locale e lo accoda nella outbox', async () => {
    const created = await service.create('  Rossi S.r.l. ', '');

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.name).toBe('Rossi S.r.l.');
    expect(created.email).toBeNull();
    expect(enqueued).toEqual([['customers', 'create', { id: created.id, name: 'Rossi S.r.l.', email: null }]]);
    expect(service.customers().map((c) => c.id)).toEqual([created.id]);
  });

  it('cancella il cliente in locale e accoda il DELETE', async () => {
    customers.push({ id: 'a', tenantId: TENANT, name: 'Rossi', email: null, updatedAt: '' });

    await service.remove('a');

    expect(customers).toEqual([]);
    expect(enqueued).toEqual([['customers', 'delete', { id: 'a' }]]);
    expect(service.customers()).toEqual([]);
  });

  it('un cliente cancellato ma non ancora inviato non torna dal server', async () => {
    pending.set('a', 'delete');
    const done = service.load();
    await tick();
    http.expectOne('/api/customers').flush([
      { id: 'a', name: 'Rossi', email: null, version: 0, updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'b', name: 'Verdi', email: null, version: 0, updatedAt: '2026-01-01T00:00:00Z' },
    ]);
    await done;

    expect(customers.map((c) => c.id)).toEqual(['b']);
    expect(service.customers().map((c) => c.id)).toEqual(['b']);
  });
});
