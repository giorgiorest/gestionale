import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../api/api-url';
import { ProfiloService } from '../profilo/profilo.service';
import { SincronizzazioneService } from '../sincronizzazione/sincronizzazione.service';
import { AllegatoLocale, CaricamentoInAttesa, DbLocale, FileInCache, LOCAL_DB } from '../../db/db-locale';
import { AllegatiService, AllegatoNonAmmessoError, DIMENSIONE_MASSIMA_BYTE, tipoDelFile } from './allegati.service';

const TENANT = 'tenant-1';
const REPORT = 'report-1';

/** Tabella in memoria con le operazioni usate dal servizio (jsdom non ha IndexedDB). */
function fakeTable<T extends { id: string }>(rows: Map<string, T>, sortKey?: keyof T) {
  const sorted = () => [...rows.values()].sort((a, b) => String(a[sortKey!]).localeCompare(String(b[sortKey!])));
  return {
    get: async (id: string) => rows.get(id),
    put: async (row: T) => {
      rows.set(row.id, row);
      return row.id;
    },
    update: async (id: string, changes: Partial<T>) => {
      Object.assign(rows.get(id)!, changes);
      return 1;
    },
    delete: async (id: string) => {
      rows.delete(id);
    },
    count: async () => rows.size,
    toArray: async () => [...rows.values()],
    bulkPut: async (list: T[]) => list.forEach((r) => rows.set(r.id, r)),
    bulkDelete: async (ids: string[]) => ids.forEach((id) => rows.delete(id)),
    orderBy: () => ({ toArray: async () => sorted() }),
    where: () => ({
      equals: (key: string[]) => ({
        toArray: async () =>
          [...rows.values()].filter((r) => {
            const e = r as unknown as { entityType: string; entityId: string };
            return e.entityType === key[0] && e.entityId === key[1];
          }),
      }),
    }),
  };
}

describe('AllegatiService', () => {
  let attachments: Map<string, AllegatoLocale>;
  let uploads: Map<string, CaricamentoInAttesa>;
  let cache: Map<string, FileInCache>;
  let enqueued: unknown[][];
  let service: AllegatiService;
  let http: HttpTestingController;
  const realFetch = globalThis.fetch;

  const photo = (name = 'foto.jpg', type = 'image/jpeg') => new File([new Uint8Array([1, 2, 3])], name, { type });
  const tick = () => new Promise((resolve) => setTimeout(resolve));

  beforeEach(() => {
    attachments = new Map();
    uploads = new Map();
    cache = new Map();
    enqueued = [];
    const db = {
      attachments: fakeTable(attachments, 'updatedAt'),
      pendingUploads: fakeTable(uploads, 'createdAt'),
      fileCache: fakeTable(cache, 'lastAccess'),
    } as unknown as DbLocale;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: db },
        { provide: ProfiloService, useValue: { load: async () => ({ tenant: { id: TENANT } }) } },
        {
          provide: SincronizzazioneService,
          useValue: {
            flush: async () => 0,
            pendingByEntity: async () => new Map(),
            enqueue: async (...args: unknown[]) => {
              enqueued.push(args);
              return {};
            },
          },
        },
      ],
    });
    service = TestBed.inject(AllegatiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it('rifiuta i tipi non ammessi e i file troppo grandi', () => {
    expect(() => service.validate(photo('virus.exe', 'application/x-msdownload'))).toThrow(AllegatoNonAmmessoError);
    const big = { name: 'a.pdf', type: 'application/pdf', size: DIMENSIONE_MASSIMA_BYTE + 1 } as File;
    expect(() => service.validate(big)).toThrow(AllegatoNonAmmessoError);
    expect(service.validate(photo())).toBe('image/jpeg');
  });

  it("ricava il tipo dall'estensione se il telefono non lo dichiara", () => {
    expect(tipoDelFile(photo('IMG_0001.HEIC', ''))).toBe('image/heic');
    expect(tipoDelFile(photo('relazione.pdf', ''))).toBe('application/pdf');
    expect(tipoDelFile(photo('note.txt', ''))).toBe('');
  });

  it('senza campo il file resta sul dispositivo e non si conferma nulla', async () => {
    await service.add('daily_reports', REPORT, photo());
    await tick();

    http.expectOne('/api/attachments/upload-url').error(new ProgressEvent('error'), { status: 0 });
    await tick();

    expect(uploads.size).toBe(1);
    expect(attachments.size).toBe(1);
    expect(enqueued).toEqual([]);
    expect([...uploads.values()][0].attempts).toBe(1);
  });

  it("con il campo carica sull'archivio, poi accoda la conferma e tiene il file in cache", async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    globalThis.fetch = (async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return new Response(null, { status: 200 });
    }) as typeof fetch;

    await service.add('daily_reports', REPORT, photo());
    await tick();
    const request = http.expectOne('/api/attachments/upload-url');
    expect(request.request.body).toMatchObject({ entityType: 'daily_reports', entityId: REPORT, mimeType: 'image/jpeg', sizeBytes: 3 });
    request.flush({ uploadUrl: 'https://archivio.example/put', expiresAt: '2026-01-01T00:00:00Z' });
    await tick();
    await tick();

    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe('https://archivio.example/put');
    expect(calls[0].init?.method).toBe('PUT');
    expect(calls[0].init?.headers).toEqual({ 'Content-Type': 'image/jpeg' });
    expect(enqueued).toHaveLength(1);
    expect(enqueued[0][0]).toBe('attachments');
    expect(enqueued[0][1]).toBe('create');
    expect((enqueued[0][2] as { sha256: string }).sha256).toMatch(/^[0-9a-f]{64}$/);
    expect(uploads.size).toBe(0);
    expect(cache.size).toBe(1);
  });

  it('un rifiuto definitivo del server ferma il file senza bloccare gli altri', async () => {
    await service.add('daily_reports', REPORT, photo());
    await tick();
    http.expectOne('/api/attachments/upload-url').flush({ detail: 'Tipo di file non ammesso' }, { status: 400, statusText: 'Bad Request' });
    await tick();

    const only = [...uploads.values()][0];
    expect(only.lastError).toBe('Tipo di file non ammesso');
    expect(enqueued).toEqual([]);
  });

  it('rimuovere un allegato lo toglie da elenco, file e cache e accoda la cancellazione', async () => {
    attachments.set('a1', { id: 'a1', entityType: 'daily_reports', entityId: REPORT } as AllegatoLocale);
    cache.set('a1', { id: 'a1', blob: new Blob(['x']), sizeBytes: 1, lastAccess: 1 });

    await service.remove('a1');

    expect(attachments.size).toBe(0);
    expect(cache.size).toBe(0);
    expect(enqueued).toEqual([['attachments', 'delete', { id: 'a1' }]]);
  });

  it('apre dalla cache senza rete e aggiorna l’ultimo utilizzo', async () => {
    const blob = new Blob(['x']);
    cache.set('a1', { id: 'a1', blob, sizeBytes: 1, lastAccess: 1 });

    const result = await service.fetchFile({ id: 'a1', mimeType: 'image/jpeg' } as AllegatoLocale);

    expect(result).toBe(blob);
    expect(cache.get('a1')!.lastAccess).toBeGreaterThan(1);
    http.expectNone('/api/attachments/a1/download-url');
  });

  it('la cache supera il limite: esce il file usato meno di recente', async () => {
    const MB = 1024 * 1024;
    cache.set('vecchio', { id: 'vecchio', blob: new Blob(['x']), sizeBytes: 60 * MB, lastAccess: 1 });
    cache.set('recente', { id: 'recente', blob: new Blob(['x']), sizeBytes: 60 * MB, lastAccess: 2 });
    globalThis.fetch = (async () => new Response('byte', { status: 200 })) as typeof fetch;

    const pending = service.fetchFile({ id: 'nuovo', mimeType: 'image/jpeg' } as AllegatoLocale);
    await tick();
    http.expectOne('/api/attachments/nuovo/download-url').flush({ downloadUrl: 'https://archivio.example/get' });
    await pending;

    expect([...cache.keys()].sort()).toEqual(['nuovo', 'recente']);
  });

  it('offline e non in cache: errore comprensibile', async () => {
    const pending = service.fetchFile({ id: 'a9', mimeType: 'image/jpeg' } as AllegatoLocale);
    await tick();
    http.expectOne('/api/attachments/a9/download-url').error(new ProgressEvent('error'), { status: 0 });

    await expect(pending).rejects.toThrow(/non disponibile/);
  });
});
