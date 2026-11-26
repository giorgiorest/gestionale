import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { PageDto } from '../../core/api/page';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalCustomer, LocalDb, OutboxOperation } from '../../db/local-db';
import { CustomerRow, CustomersService } from './customers.service';

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
      delete: async (id: string) => removeById(id),
    },
  } as unknown as LocalDb;
}

function page(content: (CustomerRow & { version?: number })[], totalElements = content.length): PageDto<CustomerRow> {
  return { content, page: 0, size: 50, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 50)) };
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

  it('carica la pagina dal server con i parametri di pagina e dimensione', async () => {
    const done = service.load();
    await tick();
    const req = http.expectOne((r) => r.url === '/api/customers');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(
      page([
        { id: 'a', name: 'Bianchi', email: 'b@example.com', updatedAt: '2026-01-01T00:00:00Z' },
        { id: 'b', name: 'Verdi', email: null, updatedAt: '2026-01-01T00:00:00Z' },
      ]),
    );
    await done;

    expect(service.offline()).toBe(false);
    expect(service.customers().map((c) => c.name)).toEqual(['Bianchi', 'Verdi']);
    expect(service.totalElements()).toBe(2);
  });

  it('manda q solo quando la ricerca non è vuota', async () => {
    service.q.set('rossi');
    const done = service.load();
    await tick();
    const req = http.expectOne((r) => r.url === '/api/customers');
    expect(req.request.params.get('q')).toBe('rossi');
    req.flush(page([{ id: 'a', name: 'Rossi', email: null, updatedAt: '2026-01-01T00:00:00Z' }]));
    await done;

    expect(service.customers().map((c) => c.name)).toEqual(['Rossi']);
  });

  it('se il server non risponde ricade sulla copia locale, paginata e filtrata lato client', async () => {
    customers.push({ id: 'a', tenantId: TENANT, name: 'Locale', email: null, updatedAt: '' });
    customers.push({ id: 'b', tenantId: TENANT, name: 'Altro tenant', email: null, updatedAt: '' });
    customers[1].tenantId = 'altro-tenant';
    const done = service.load();
    await tick();
    http.expectOne((r) => r.url === '/api/customers').error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.customers().map((c) => c.name)).toEqual(['Locale']);
    expect(service.totalElements()).toBe(1);
  });

  it('una cancellazione locale non ancora inviata non torna dal server', async () => {
    pending.set('a', 'delete');
    const done = service.load();
    await tick();
    http
      .expectOne((r) => r.url === '/api/customers')
      .flush(
        page([
          { id: 'a', name: 'Rossi', email: null, updatedAt: '2026-01-01T00:00:00Z' },
          { id: 'b', name: 'Verdi', email: null, updatedAt: '2026-01-01T00:00:00Z' },
        ]),
      );
    await done;

    expect(service.customers().map((c) => c.id)).toEqual(['b']);
  });

  it('crea il cliente in locale, lo accoda nella outbox e lo mostra subito', async () => {
    const created = await service.create('  Rossi S.r.l. ', '');

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.name).toBe('Rossi S.r.l.');
    expect(created.email).toBeNull();
    expect(enqueued).toEqual([['customers', 'create', { id: created.id, name: 'Rossi S.r.l.', email: null }]]);
    expect(service.customers().map((c) => c.id)).toEqual([created.id]);
    expect(service.totalElements()).toBe(1);
  });

  it('cancella il cliente in locale, accoda il DELETE e lo toglie subito dalla pagina', async () => {
    customers.push({ id: 'a', tenantId: TENANT, name: 'Rossi', email: null, updatedAt: '' });

    await service.remove('a');

    expect(customers).toEqual([]);
    expect(enqueued).toEqual([['customers', 'delete', { id: 'a' }]]);
    expect(service.customers()).toEqual([]);
  });
});
