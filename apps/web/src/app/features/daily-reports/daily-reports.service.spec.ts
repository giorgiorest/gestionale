import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../../core/api/api-url';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB, LocalDailyReport, LocalDb, OutboxOperation } from '../../db/local-db';
import { DailyReportsService } from './daily-reports.service';

const TENANT = 'tenant-1';
const COMMESSA = 'commessa-1';

function fakeDb(reports: LocalDailyReport[]): LocalDb {
  const removeById = (id: string) =>
    reports.splice(
      reports.findIndex((x) => x.id === id),
      1,
    );
  return {
    dailyReports: {
      where: (field: keyof LocalDailyReport) => ({
        equals: (value: string) => ({
          toArray: async () => reports.filter((r) => r[field] === value),
        }),
      }),
      bulkPut: async (list: LocalDailyReport[]) => {
        for (const r of list) {
          const i = reports.findIndex((x) => x.id === r.id);
          if (i >= 0) reports[i] = r;
          else reports.push(r);
        }
      },
      bulkDelete: async (ids: string[]) => ids.forEach(removeById),
      put: async (r: LocalDailyReport) => {
        const i = reports.findIndex((x) => x.id === r.id);
        if (i >= 0) reports[i] = r;
        else reports.push(r);
        return r.id;
      },
      delete: async (id: string) => removeById(id),
    },
  } as unknown as LocalDb;
}

describe('DailyReportsService', () => {
  let reports: LocalDailyReport[];
  /** Coda finta: id → operazione, come la restituisce SyncService.pendingByEntity(). */
  let pending: Map<string, OutboxOperation>;
  let enqueued: unknown[];
  let service: DailyReportsService;
  let http: HttpTestingController;

  beforeEach(() => {
    reports = [];
    pending = new Map();
    enqueued = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: fakeDb(reports) },
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
    service = TestBed.inject(DailyReportsService);
    http = TestBed.inject(HttpTestingController);
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  it('carica dal server, salva la copia locale e ordina per data decrescente', async () => {
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/daily-reports`).flush([
      {
        id: 'a',
        commessaId: COMMESSA,
        date: '2026-06-01',
        weather: 'Sereno',
        notes: null,
        status: 'draft',
        submittedAt: null,
        closedAt: null,
        updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'b',
        commessaId: COMMESSA,
        date: '2026-06-02',
        weather: 'Pioggia',
        notes: null,
        status: 'draft',
        submittedAt: null,
        closedAt: null,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]);
    await done;

    expect(service.offline()).toBe(false);
    expect(service.reports().map((r) => r.id)).toEqual(['b', 'a']);
  });

  it('se il server non risponde usa la copia locale', async () => {
    reports.push({
      id: 'a',
      tenantId: TENANT,
      commessaId: COMMESSA,
      date: '2026-06-01',
      weather: 'Locale',
      notes: null,
      status: 'draft',
      submittedAt: null,
      closedAt: null,
      updatedAt: '',
    });
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/daily-reports`).error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.reports().map((r) => r.id)).toEqual(['a']);
  });

  it('crea il rapportino in locale, sempre come draft, e lo accoda nella outbox', async () => {
    const created = await service.create(COMMESSA, '2026-06-01', ' Sereno ', null);

    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
    expect(created.status).toBe('draft');
    expect(created.weather).toBe('Sereno');
    expect(enqueued).toEqual([
      [
        'daily-reports',
        'create',
        { id: created.id, commessaId: COMMESSA, date: '2026-06-01', weather: 'Sereno', notes: null },
      ],
    ]);
    expect(service.reports().map((r) => r.id)).toEqual([created.id]);
  });

  it('aggiorna meteo e note e accoda un update', async () => {
    const created = await service.create(COMMESSA, '2026-06-01', 'Sereno', null);
    enqueued.length = 0;

    await service.update(created, 'Pioggia', 'Rallentamenti');

    expect(enqueued).toEqual([
      [
        'daily-reports',
        'update',
        { id: created.id, commessaId: COMMESSA, date: '2026-06-01', weather: 'Pioggia', notes: 'Rallentamenti' },
      ],
    ]);
    expect(service.reports()[0].weather).toBe('Pioggia');
  });

  it('cancella il rapportino in locale e accoda il DELETE', async () => {
    reports.push({
      id: 'a',
      tenantId: TENANT,
      commessaId: COMMESSA,
      date: '2026-06-01',
      weather: null,
      notes: null,
      status: 'draft',
      submittedAt: null,
      closedAt: null,
      updatedAt: '',
    });

    await service.remove('a', COMMESSA);

    expect(reports).toEqual([]);
    expect(enqueued).toEqual([['daily-reports', 'delete', { id: 'a' }]]);
    expect(service.reports()).toEqual([]);
  });

  it('un rapportino cancellato ma non ancora inviato non torna dal server', async () => {
    pending.set('a', 'delete');
    const done = service.load(COMMESSA);
    await tick();
    http.expectOne(`/api/commesse/${COMMESSA}/daily-reports`).flush([
      {
        id: 'a',
        commessaId: COMMESSA,
        date: '2026-06-01',
        weather: null,
        notes: null,
        status: 'draft',
        submittedAt: null,
        closedAt: null,
        updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        id: 'b',
        commessaId: COMMESSA,
        date: '2026-06-02',
        weather: null,
        notes: null,
        status: 'draft',
        submittedAt: null,
        closedAt: null,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]);
    await done;

    expect(reports.map((r) => r.id)).toEqual(['b']);
    expect(service.reports().map((r) => r.id)).toEqual(['b']);
  });
});
