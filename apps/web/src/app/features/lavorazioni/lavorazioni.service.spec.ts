import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { ProfiloService } from '../../core/profilo/profilo.service';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { LOCAL_DB, DbLocale, LavorazioneLocale, OutboxOperation } from '../../db/db-locale';
import { LavorazioniService } from './lavorazioni.service';

const TENANT = 'tenant-1';
const COMMESSA = 'commessa-1';

function fakeDb(activities: LavorazioneLocale[]): DbLocale {
  const removeById = (id: string) =>
    activities.splice(
      activities.findIndex((x) => x.id === id),
      1,
    );
  return {
    workActivities: {
      where: (field: keyof LavorazioneLocale) => ({
        equals: (value: string) => ({
          toArray: async () => activities.filter((a) => a[field] === value),
        }),
      }),
      bulkPut: async (list: LavorazioneLocale[]) => {
        for (const a of list) {
          const i = activities.findIndex((x) => x.id === a.id);
          if (i >= 0) activities[i] = a;
          else activities.push(a);
        }
      },
      bulkDelete: async (ids: string[]) => ids.forEach(removeById),
      put: async (a: LavorazioneLocale) => {
        activities.push(a);
        return a.id;
      },
      delete: async (id: string) => removeById(id),
    },
  } as unknown as DbLocale;
}

describe('WorkActivitiesService', () => {
  let activities: LavorazioneLocale[];
  /** Coda finta: id → operazione, come la restituisce SincronizzazioneService.pendingByEntity(). */
  let pending: Map<string, OutboxOperation>;
  let enqueued: unknown[];
  let service: LavorazioniService;
  let http: HttpTestingController;

  beforeEach(() => {
    activities = [];
    pending = new Map();
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(activities) },
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
    service = TestBed.inject(LavorazioniService);
    http = TestBed.inject(HttpTestingController);
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  it('carica dal server, salva la copia locale e ordina per codice', async () => {
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/work-activities`).flush([
      {
        id: 'b',
        commessaId: COMMESSA,
        parentId: null,
        code: 'B-01',
        name: 'Muri',
        description: null,
        status: 'pianificata',
        progressPercentage: 0,
        updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'a',
        commessaId: COMMESSA,
        parentId: null,
        code: 'A-01',
        name: 'Fondazioni',
        description: null,
        status: 'in_corso',
        progressPercentage: 50,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]);
    await done;

    expect(service.offline()).toBe(false);
    expect(service.activities().map((a) => a.id)).toEqual(['a', 'b']);
  });

  it('se il server non risponde usa la copia locale', async () => {
    activities.push({
      id: 'a',
      tenantId: TENANT,
      commessaId: COMMESSA,
      parentId: null,
      code: 'A-01',
      name: 'Locale',
      description: null,
      status: 'pianificata',
      progressPercentage: 0,
      updatedAt: '',
    });
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/work-activities`).error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.activities().map((a) => a.id)).toEqual(['a']);
  });

  it('crea la lavorazione in locale e la accoda nella outbox', async () => {
    const created = await service.create(COMMESSA, ' A-01 ', ' Fondazioni ');

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.code).toBe('A-01');
    expect(created.name).toBe('Fondazioni');
    expect(created.commessaId).toBe(COMMESSA);
    expect(enqueued).toEqual([
      [
        'work-activities',
        'create',
        {
          id: created.id,
          commessaId: COMMESSA,
          parentId: null,
          code: 'A-01',
          name: 'Fondazioni',
          description: null,
          status: 'pianificata',
          progressPercentage: 0,
        },
      ],
    ]);
    expect(service.activities().map((a) => a.id)).toEqual([created.id]);
  });

  it('cancella la lavorazione in locale e accoda il DELETE', async () => {
    activities.push({
      id: 'a',
      tenantId: TENANT,
      commessaId: COMMESSA,
      parentId: null,
      code: 'A-01',
      name: 'Fondazioni',
      description: null,
      status: 'pianificata',
      progressPercentage: 0,
      updatedAt: '',
    });

    await service.remove('a', COMMESSA);

    expect(activities).toEqual([]);
    expect(enqueued).toEqual([['work-activities', 'delete', { id: 'a' }]]);
    expect(service.activities()).toEqual([]);
  });

  it('una lavorazione cancellata ma non ancora inviata non torna dal server', async () => {
    pending.set('a', 'delete');
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/work-activities`).flush([
      {
        id: 'a',
        commessaId: COMMESSA,
        parentId: null,
        code: 'A-01',
        name: 'Fondazioni',
        description: null,
        status: 'pianificata',
        progressPercentage: 0,
        updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'b',
        commessaId: COMMESSA,
        parentId: null,
        code: 'B-01',
        name: 'Muri',
        description: null,
        status: 'pianificata',
        progressPercentage: 0,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]);
    await done;

    expect(activities.map((a) => a.id)).toEqual(['b']);
    expect(service.activities().map((a) => a.id)).toEqual(['b']);
  });
});
