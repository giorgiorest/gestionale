import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalDb, LocalResource, OutboxOperation } from '../../db/local-db';
import { ResourcesService } from './resources.service';

const TENANT = 'tenant-1';

function fakeDb(resources: LocalResource[]): LocalDb {
  const removeById = (id: string) =>
    resources.splice(
      resources.findIndex((x) => x.id === id),
      1,
    );
  return {
    resources: {
      toArray: async () => [...resources],
      put: async (r: LocalResource) => {
        resources.push(r);
        return r.id;
      },
      bulkPut: async (list: LocalResource[]) => {
        for (const r of list) {
          const i = resources.findIndex((x) => x.id === r.id);
          if (i >= 0) resources[i] = r;
          else resources.push(r);
        }
      },
      bulkDelete: async (ids: string[]) => ids.forEach(removeById),
      delete: async (id: string) => removeById(id),
    },
  } as unknown as LocalDb;
}

describe('ResourcesService', () => {
  let resources: LocalResource[];
  /** Coda finta: id → operazione, come la restituisce SyncService.pendingByEntity(). */
  let pending: Map<string, OutboxOperation>;
  let enqueued: unknown[];
  let service: ResourcesService;
  let http: HttpTestingController;

  beforeEach(() => {
    resources = [];
    pending = new Map();
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(resources) },
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
    service = TestBed.inject(ResourcesService);
    http = TestBed.inject(HttpTestingController);
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  it('carica dal server, salva la copia locale e ordina per cognome e nome', async () => {
    resources.push({
      id: 'old',
      tenantId: TENANT,
      firstName: 'Rimosso',
      lastName: 'Dal server',
      userId: null,
      teamLeadId: null,
      updatedAt: '',
    });
    const done = service.load();
    await tick();
    http.expectOne('/api/resources').flush([
      { id: 'b', firstName: 'Mario', lastName: 'Verdi', userId: null, teamLeadId: null, updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'a', firstName: 'Anna', lastName: 'Bianchi', userId: null, teamLeadId: null, updatedAt: '2026-01-01T00:00:00Z' },
    ]);
    await done;

    expect(service.offline()).toBe(false);
    expect(service.resources().map((r) => r.lastName)).toEqual(['Bianchi', 'Verdi']);
    expect(resources.map((r) => r.id).sort()).toEqual(['a', 'b']);
  });

  it('se il server non risponde usa la copia locale', async () => {
    resources.push({
      id: 'a',
      tenantId: TENANT,
      firstName: 'Mario',
      lastName: 'Locale',
      userId: null,
      teamLeadId: null,
      updatedAt: '',
    });
    const done = service.load();
    await tick();
    http.expectOne('/api/resources').error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.resources().map((r) => r.lastName)).toEqual(['Locale']);
  });

  it('crea la risorsa in locale e la accoda nella outbox', async () => {
    const created = await service.create('  Mario ', ' Rossi ', null);

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.firstName).toBe('Mario');
    expect(created.lastName).toBe('Rossi');
    expect(created.teamLeadId).toBeNull();
    expect(enqueued).toEqual([
      ['resources', 'create', { id: created.id, firstName: 'Mario', lastName: 'Rossi', userId: null, teamLeadId: null }],
    ]);
    expect(service.resources().map((r) => r.id)).toEqual([created.id]);
  });

  it('cancella la risorsa in locale e accoda il DELETE', async () => {
    resources.push({
      id: 'a',
      tenantId: TENANT,
      firstName: 'Mario',
      lastName: 'Rossi',
      userId: null,
      teamLeadId: null,
      updatedAt: '',
    });

    await service.remove('a');

    expect(resources).toEqual([]);
    expect(enqueued).toEqual([['resources', 'delete', { id: 'a' }]]);
    expect(service.resources()).toEqual([]);
  });

  it('una risorsa cancellata ma non ancora inviata non torna dal server', async () => {
    pending.set('a', 'delete');
    const done = service.load();
    await tick();
    http.expectOne('/api/resources').flush([
      { id: 'a', firstName: 'Mario', lastName: 'Rossi', userId: null, teamLeadId: null, updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'b', firstName: 'Anna', lastName: 'Verdi', userId: null, teamLeadId: null, updatedAt: '2026-01-01T00:00:00Z' },
    ]);
    await done;

    expect(resources.map((r) => r.id)).toEqual(['b']);
    expect(service.resources().map((r) => r.id)).toEqual(['b']);
  });
});
