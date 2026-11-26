import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { PageDto } from '../../core/api/page';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalDb, LocalSupplier, OutboxOperation } from '../../db/local-db';
import { SupplierRow, SuppliersService } from './suppliers.service';

const TENANT = 'tenant-1';

function fakeDb(suppliers: LocalSupplier[]): LocalDb {
  const removeById = (id: string) =>
    suppliers.splice(
      suppliers.findIndex((x) => x.id === id),
      1,
    );
  return {
    suppliers: {
      toArray: async () => [...suppliers],
      put: async (s: LocalSupplier) => {
        suppliers.push(s);
        return s.id;
      },
      delete: async (id: string) => removeById(id),
    },
  } as unknown as LocalDb;
}

function page(content: (SupplierRow & { version?: number })[], totalElements = content.length): PageDto<SupplierRow> {
  return { content, page: 0, size: 50, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 50)) };
}

function supplierRow(fields: Partial<SupplierRow> & { id: string; name: string }): SupplierRow {
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

function localSupplier(fields: Partial<LocalSupplier> & { id: string; tenantId: string; name: string }): LocalSupplier {
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

/** Stesso contratto di {@code CustomersService}, entità e tabella proprie (roadmap 2.2). */
describe('SuppliersService', () => {
  let suppliers: LocalSupplier[];
  let pending: Map<string, OutboxOperation>;
  let enqueued: unknown[];
  let service: SuppliersService;
  let http: HttpTestingController;

  beforeEach(() => {
    suppliers = [];
    pending = new Map();
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(suppliers) },
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
    service = TestBed.inject(SuppliersService);
    http = TestBed.inject(HttpTestingController);
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  it('carica la pagina dal server con i parametri di pagina e dimensione', async () => {
    const done = service.load();
    await tick();
    const req = http.expectOne((r) => r.url === '/api/suppliers');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([supplierRow({ id: 'a', name: 'Edilcantieri' }), supplierRow({ id: 'b', name: 'Forniture' })]));
    await done;

    expect(service.offline()).toBe(false);
    expect(service.suppliers().map((s) => s.name)).toEqual(['Edilcantieri', 'Forniture']);
    expect(service.totalElements()).toBe(2);
  });

  it('se il server non risponde ricade sulla copia locale', async () => {
    suppliers.push(localSupplier({ id: 'a', tenantId: TENANT, name: 'Locale' }));
    suppliers.push(localSupplier({ id: 'b', tenantId: 'altro-tenant', name: 'Altro tenant' }));
    const done = service.load();
    await tick();
    http.expectOne((r) => r.url === '/api/suppliers').error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.suppliers().map((s) => s.name)).toEqual(['Locale']);
  });

  it('crea il fornitore in locale, lo accoda nella outbox e lo mostra subito', async () => {
    const created = await service.create('  Edilcantieri S.r.l. ', null, { partitaIva: '12345678901' });

    expect(created.name).toBe('Edilcantieri S.r.l.');
    expect(created.partitaIva).toBe('12345678901');
    expect(enqueued).toEqual([
      [
        'suppliers',
        'create',
        {
          id: created.id,
          name: 'Edilcantieri S.r.l.',
          email: null,
          partitaIva: '12345678901',
          codiceFiscale: null,
          indirizzo: null,
          pec: null,
          codiceSdi: null,
          condizioniPagamento: null,
        },
      ],
    ]);
    expect(service.suppliers().map((s) => s.id)).toEqual([created.id]);
  });

  it('cancella il fornitore in locale, accoda il DELETE e lo toglie subito dalla pagina', async () => {
    suppliers.push(localSupplier({ id: 'a', tenantId: TENANT, name: 'Edilcantieri' }));

    await service.remove('a');

    expect(suppliers).toEqual([]);
    expect(enqueued).toEqual([['suppliers', 'delete', { id: 'a' }]]);
    expect(service.suppliers()).toEqual([]);
  });
});
