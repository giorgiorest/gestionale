import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../api/api-url';
import { ProfiloService } from '../profilo/profilo.service';
import { SincronizzazioneService } from '../sincronizzazione/sincronizzazione.service';
import { LOCAL_DB, DbLocale, NotificaLocale } from '../../db/db-locale';
import { NotificheService } from './notifiche.service';

function notification(overrides: Partial<NotificaLocale> = {}): NotificaLocale {
  return {
    id: 'n1',
    kind: 'announcement',
    type: 'announcement',
    title: 'Regole di sicurezza',
    body: 'Leggere',
    link: null,
    senderName: 'Anna',
    createdAt: '2026-11-23T10:00:00Z',
    requiresAck: true,
    readAt: null,
    acknowledgedAt: null,
    ...overrides,
  };
}

describe('NotificationsService', () => {
  let stored: Map<string, NotificaLocale>;
  let enqueued: unknown[][];
  let service: NotificheService;
  let http: HttpTestingController;

  beforeEach(() => {
    stored = new Map();
    enqueued = [];
    const db = {
      notifications: {
        put: async (n: NotificaLocale) => stored.set(n.id, n),
        get: async (id: string) => stored.get(id),
        toArray: async () => [...stored.values()],
        clear: async () => stored.clear(),
        bulkPut: async (list: NotificaLocale[]) => list.forEach((n) => stored.set(n.id, n)),
      },
    } as unknown as DbLocale;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: LOCAL_DB, useValue: db },
        { provide: ProfiloService, useValue: { load: async () => null } },
        {
          provide: SincronizzazioneService,
          useValue: {
            enqueue: async (...args: unknown[]) => enqueued.push(args),
            flush: async () => 0,
          },
        },
      ],
    });
    service = TestBed.inject(NotificheService);
    http = TestBed.inject(HttpTestingController);
  });

  it('la presa visione passa dalla outbox e aggiorna subito la copia locale e i contatori', async () => {
    service.counts.set({ unread: 1, toAcknowledge: 1 });

    const updated = await service.acknowledge(notification());

    expect(updated.acknowledgedAt).not.toBeNull();
    expect(updated.readAt).not.toBeNull();
    expect(enqueued).toEqual([['notifications', 'update', { id: 'n1', acknowledged: true }]]);
    expect(stored.get('n1')?.acknowledgedAt).toBe(updated.acknowledgedAt);
    expect(service.counts()).toEqual({ unread: 0, toAcknowledge: 0 });
    http.match('/api/notifications/counts').forEach((r) => r.flush({ unread: 0, toAcknowledge: 0 }));
  });

  it('non rimanda letture o conferme già fatte', async () => {
    await service.markRead(notification({ readAt: '2026-11-23T10:05:00Z' }));
    await service.acknowledge(notification({ acknowledgedAt: '2026-11-23T10:06:00Z' }));
    await service.acknowledge(notification({ requiresAck: false }));
    expect(enqueued).toEqual([]);
  });

  it('senza rete mostra le notifiche salvate sul dispositivo', async () => {
    stored.set('n1', notification());
    const done = service.load();
    await new Promise((r) => setTimeout(r));
    http.expectOne('/api/notifications').error(new ProgressEvent('error'));
    await done;

    expect(service.offline()).toBe(true);
    expect(service.notifications().map((n) => n.title)).toEqual(['Regole di sicurezza']);
  });

  it('il badge mostra il maggiore tra non lette e da confermare', () => {
    service.counts.set({ unread: 2, toAcknowledge: 3 });
    expect(service.badge()).toBe(3);
  });
});
