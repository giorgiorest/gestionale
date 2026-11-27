import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { ProfiloService } from '../../core/profilo/profilo.service';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { LOCAL_DB, DbLocale, AssegnazioneRisorsaLocale, OutboxOperation } from '../../db/db-locale';
import { AssegnazioniService } from './assegnazioni.service';

const TENANT = 'tenant-1';
const COMMESSA = 'commessa-1';

function fakeDb(assignments: AssegnazioneRisorsaLocale[]): DbLocale {
  const removeById = (id: string) =>
    assignments.splice(
      assignments.findIndex((x) => x.id === id),
      1,
    );
  return {
    resourceAssignments: {
      where: (field: keyof AssegnazioneRisorsaLocale) => ({
        equals: (value: string) => ({
          toArray: async () => assignments.filter((a) => a[field] === value),
        }),
      }),
      bulkPut: async (list: AssegnazioneRisorsaLocale[]) => {
        for (const a of list) {
          const i = assignments.findIndex((x) => x.id === a.id);
          if (i >= 0) assignments[i] = a;
          else assignments.push(a);
        }
      },
      bulkDelete: async (ids: string[]) => ids.forEach(removeById),
      put: async (a: AssegnazioneRisorsaLocale) => {
        assignments.push(a);
        return a.id;
      },
      delete: async (id: string) => removeById(id),
    },
  } as unknown as DbLocale;
}

describe('ResourceAssignmentsService', () => {
  let assignments: AssegnazioneRisorsaLocale[];
  /** Coda finta: id → operazione, come la restituisce SincronizzazioneService.pendingByEntity(). */
  let pending: Map<string, OutboxOperation>;
  let enqueued: unknown[];
  let service: AssegnazioniService;
  let http: HttpTestingController;

  beforeEach(() => {
    assignments = [];
    pending = new Map();
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(assignments) },
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
    service = TestBed.inject(AssegnazioniService);
    http = TestBed.inject(HttpTestingController);
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  it('carica dal server, salva la copia locale e ordina per inizio periodo', async () => {
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/resource-assignments`).flush([
      {
        id: 'b',
        resourceId: 'r-2',
        commessaId: COMMESSA,
        validFrom: '2026-03-01',
        validTo: null,
        role: 'operaio',
        hourlyCostOverride: null,
        active: true,
        updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'a',
        resourceId: 'r-1',
        commessaId: COMMESSA,
        validFrom: '2026-01-01',
        validTo: '2026-02-28',
        role: 'capocantiere',
        hourlyCostOverride: null,
        active: true,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]);
    await done;

    expect(service.offline()).toBe(false);
    expect(service.assignments().map((a) => a.id)).toEqual(['a', 'b']);
  });

  it('se il server non risponde usa la copia locale', async () => {
    assignments.push({
      id: 'a',
      tenantId: TENANT,
      resourceId: 'r-1',
      commessaId: COMMESSA,
      validFrom: '2026-01-01',
      validTo: null,
      role: 'operaio',
      hourlyCostOverride: null,
      active: true,
      updatedAt: '',
    });
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/resource-assignments`).error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.assignments().map((a) => a.id)).toEqual(['a']);
  });

  it("crea l'assegnazione in locale e la accoda nella outbox", async () => {
    const created = await service.create(COMMESSA, 'r-1', ' capocantiere ', '2026-01-01', null);

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.role).toBe('capocantiere');
    expect(created.commessaId).toBe(COMMESSA);
    expect(enqueued).toEqual([
      [
        'resource-assignments',
        'create',
        {
          id: created.id,
          resourceId: 'r-1',
          commessaId: COMMESSA,
          validFrom: '2026-01-01',
          validTo: null,
          role: 'capocantiere',
          hourlyCostOverride: null,
          active: true,
        },
      ],
    ]);
    expect(service.assignments().map((a) => a.id)).toEqual([created.id]);
  });

  it("cancella l'assegnazione in locale e accoda il DELETE", async () => {
    assignments.push({
      id: 'a',
      tenantId: TENANT,
      resourceId: 'r-1',
      commessaId: COMMESSA,
      validFrom: '2026-01-01',
      validTo: null,
      role: 'operaio',
      hourlyCostOverride: null,
      active: true,
      updatedAt: '',
    });

    await service.remove('a', COMMESSA);

    expect(assignments).toEqual([]);
    expect(enqueued).toEqual([['resource-assignments', 'delete', { id: 'a' }]]);
    expect(service.assignments()).toEqual([]);
  });

  it('un\'assegnazione cancellata ma non ancora inviata non torna dal server', async () => {
    pending.set('a', 'delete');
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/resource-assignments`).flush([
      {
        id: 'a',
        resourceId: 'r-1',
        commessaId: COMMESSA,
        validFrom: '2026-01-01',
        validTo: null,
        role: 'operaio',
        hourlyCostOverride: null,
        active: true,
        updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'b',
        resourceId: 'r-2',
        commessaId: COMMESSA,
        validFrom: '2026-01-01',
        validTo: null,
        role: 'operaio',
        hourlyCostOverride: null,
        active: true,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]);
    await done;

    expect(assignments.map((a) => a.id)).toEqual(['b']);
    expect(service.assignments().map((a) => a.id)).toEqual(['b']);
  });
});
