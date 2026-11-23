import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalCustomer, LocalDb, OutboxEntry } from '../../db/local-db';
import { CustomersService } from './customers.service';

const TENANT = 'tenant-1';

function fakeDb(customers: LocalCustomer[], outbox: OutboxEntry[]): LocalDb {
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
      bulkDelete: async (ids: string[]) => {
        for (const id of ids) customers.splice(customers.findIndex((x) => x.id === id), 1);
      },
    },
    outbox: { toArray: async () => [...outbox] },
  } as unknown as LocalDb;
}

describe('CustomersService', () => {
  let customers: LocalCustomer[];
  let outbox: OutboxEntry[];
  let enqueued: unknown[];
  let service: CustomersService;
  let http: HttpTestingController;

  beforeEach(() => {
    customers = [];
    outbox = [];
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(customers, outbox) },
        { provide: ProfileService, useValue: { load: async () => ({ tenant: { id: TENANT } }) } },
        {
          provide: SyncService,
          useValue: {
            flush: async () => 0,
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
      { id: 'b', name: 'Verdi', email: null, updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'a', name: 'Bianchi', email: 'b@example.com', updatedAt: '2026-01-01T00:00:00Z' },
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
});
