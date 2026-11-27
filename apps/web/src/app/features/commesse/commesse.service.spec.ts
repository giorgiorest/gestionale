import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { ProfiloService } from '../../core/profilo/profilo.service';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { LOCAL_DB, CommessaLocale, DbLocale, OutboxOperation } from '../../db/db-locale';
import { CommesseService } from './commesse.service';

const TENANT = 'tenant-1';

function fakeDb(commesse: CommessaLocale[]): DbLocale {
  const removeById = (id: string) =>
    commesse.splice(
      commesse.findIndex((x) => x.id === id),
      1,
    );
  return {
    commesse: {
      toArray: async () => [...commesse],
      put: async (c: CommessaLocale) => {
        commesse.push(c);
        return c.id;
      },
      bulkPut: async (list: CommessaLocale[]) => {
        for (const c of list) {
          const i = commesse.findIndex((x) => x.id === c.id);
          if (i >= 0) commesse[i] = c;
          else commesse.push(c);
        }
      },
      bulkDelete: async (ids: string[]) => ids.forEach(removeById),
      delete: async (id: string) => removeById(id),
    },
  } as unknown as DbLocale;
}

describe('CommesseService', () => {
  let commesse: CommessaLocale[];
  /** Coda finta: id → operazione, come la restituisce SincronizzazioneService.pendingByEntity(). */
  let pending: Map<string, OutboxOperation>;
  let enqueued: unknown[];
  let service: CommesseService;
  let http: HttpTestingController;

  beforeEach(() => {
    commesse = [];
    pending = new Map();
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(commesse) },
        { provide: ProfiloService, useValue: { load: async () => ({ tenant: { id: TENANT } }) } },
        {
          provide: SincronizzazioneService,
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
    service = TestBed.inject(CommesseService);
    http = TestBed.inject(HttpTestingController);
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  it('carica dal server, salva la copia locale e ordina per nome', async () => {
    commesse.push({
      id: 'old',
      tenantId: TENANT,
      customerId: 'c0',
      nome: 'Rimossa dal server',
      indirizzo: null,
      stato: 'pianificata',
      updatedAt: '',
    });
    const done = service.load();
    await tick();
    http.expectOne('/api/commesse').flush([
      { id: 'b', customerId: 'c1', nome: 'Verdi', indirizzo: null, stato: 'pianificata', updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'a', customerId: 'c2', nome: 'Bianchi', indirizzo: 'Via Roma', stato: 'in_corso', updatedAt: '2026-01-01T00:00:00Z' },
    ]);
    await done;

    expect(service.offline()).toBe(false);
    expect(service.commesse().map((c) => c.nome)).toEqual(['Bianchi', 'Verdi']);
    expect(commesse.map((c) => c.id).sort()).toEqual(['a', 'b']);
  });

  it('se il server non risponde usa la copia locale', async () => {
    commesse.push({
      id: 'a',
      tenantId: TENANT,
      customerId: 'c1',
      nome: 'Locale',
      indirizzo: null,
      stato: 'pianificata',
      updatedAt: '',
    });
    const done = service.load();
    await tick();
    http.expectOne('/api/commesse').error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.commesse().map((c) => c.nome)).toEqual(['Locale']);
  });

  it('crea la commessa in locale e la accoda nella outbox', async () => {
    const created = await service.create('c1', '  Tetto ', '', 'pianificata');

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.nome).toBe('Tetto');
    expect(created.indirizzo).toBeNull();
    expect(enqueued).toEqual([
      ['commesse', 'create', { id: created.id, customerId: 'c1', nome: 'Tetto', indirizzo: null, stato: 'pianificata' }],
    ]);
    expect(service.commesse().map((c) => c.id)).toEqual([created.id]);
  });

  it('cancella la commessa in locale e accoda il DELETE', async () => {
    commesse.push({
      id: 'a',
      tenantId: TENANT,
      customerId: 'c1',
      nome: 'Tetto',
      indirizzo: null,
      stato: 'pianificata',
      updatedAt: '',
    });

    await service.remove('a');

    expect(commesse).toEqual([]);
    expect(enqueued).toEqual([['commesse', 'delete', { id: 'a' }]]);
    expect(service.commesse()).toEqual([]);
  });

  it('una commessa cancellata ma non ancora inviata non torna dal server', async () => {
    pending.set('a', 'delete');
    const done = service.load();
    await tick();
    http.expectOne('/api/commesse').flush([
      { id: 'a', customerId: 'c1', nome: 'Tetto', indirizzo: null, stato: 'pianificata', updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'b', customerId: 'c1', nome: 'Verdi', indirizzo: null, stato: 'pianificata', updatedAt: '2026-01-01T00:00:00Z' },
    ]);
    await done;

    expect(commesse.map((c) => c.id)).toEqual(['b']);
    expect(service.commesse().map((c) => c.id)).toEqual(['b']);
  });
});
