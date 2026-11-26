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

/** Riga completa com'è restituita dall'API, con i soli campi indicati diversi dal default. */
function customerRow(fields: Partial<CustomerRow> & { id: string; name: string }): CustomerRow {
  return {
    email: null,
    partitaIva: null,
    codiceFiscale: null,
    indirizzo: null,
    pec: null,
    codiceSdi: null,
    condizioniPagamento: null,
    updatedAt: '2026-01-01T00:00:00Z',
    ...fields,
  };
}

/** Riga locale (Dexie) completa, con i soli campi indicati diversi dal default. */
function localCustomer(fields: Partial<LocalCustomer> & { id: string; tenantId: string; name: string }): LocalCustomer {
  return {
    email: null,
    partitaIva: null,
    codiceFiscale: null,
    indirizzo: null,
    pec: null,
    codiceSdi: null,
    condizioniPagamento: null,
    updatedAt: '',
    ...fields,
  };
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
        customerRow({ id: 'a', name: 'Bianchi', email: 'b@example.com' }),
        customerRow({ id: 'b', name: 'Verdi' }),
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
    req.flush(page([customerRow({ id: 'a', name: 'Rossi' })]));
    await done;

    expect(service.customers().map((c) => c.name)).toEqual(['Rossi']);
  });

  it('se il server non risponde ricade sulla copia locale, paginata e filtrata lato client', async () => {
    customers.push(localCustomer({ id: 'a', tenantId: TENANT, name: 'Locale' }));
    customers.push(localCustomer({ id: 'b', tenantId: TENANT, name: 'Altro tenant' }));
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
      .flush(page([customerRow({ id: 'a', name: 'Rossi' }), customerRow({ id: 'b', name: 'Verdi' })]));
    await done;

    expect(service.customers().map((c) => c.id)).toEqual(['b']);
  });

  it('crea il cliente in locale, lo accoda nella outbox e lo mostra subito', async () => {
    const created = await service.create('  Rossi S.r.l. ', '');

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.name).toBe('Rossi S.r.l.');
    expect(created.email).toBeNull();
    expect(enqueued).toEqual([
      [
        'customers',
        'create',
        {
          id: created.id,
          name: 'Rossi S.r.l.',
          email: null,
          partitaIva: null,
          codiceFiscale: null,
          indirizzo: null,
          pec: null,
          codiceSdi: null,
          condizioniPagamento: null,
        },
      ],
    ]);
    expect(service.customers().map((c) => c.id)).toEqual([created.id]);
    expect(service.totalElements()).toBe(1);
  });

  it('cancella il cliente in locale, accoda il DELETE e lo toglie subito dalla pagina', async () => {
    customers.push(localCustomer({ id: 'a', tenantId: TENANT, name: 'Rossi' }));

    await service.remove('a');

    expect(customers).toEqual([]);
    expect(enqueued).toEqual([['customers', 'delete', { id: 'a' }]]);
    expect(service.customers()).toEqual([]);
  });
});
