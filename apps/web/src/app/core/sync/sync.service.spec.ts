import { TestBed } from '@angular/core/testing';
import { LOCAL_DB, LocalDb, OutboxEntry } from '../../db/local-db';
import { SyncService } from './sync.service';

describe('SyncService', () => {
  let added: OutboxEntry[];
  let service: SyncService;

  beforeEach(() => {
    added = [];
    // jsdom non ha IndexedDB: si usa un finto database con la sola tabella outbox.
    const fakeDb = {
      outbox: {
        add: async (entry: OutboxEntry) => {
          added.push(entry);
          return entry.id;
        },
      },
    } as unknown as LocalDb;

    TestBed.configureTestingModule({ providers: [{ provide: LOCAL_DB, useValue: fakeDb }] });
    service = TestBed.inject(SyncService);
  });

  it('enqueue registra la modifica nella outbox', async () => {
    const entry = await service.enqueue('customers', 'create', { name: 'Rossi' });

    expect(added).toEqual([entry]);
    expect(entry.entity).toBe('customers');
    expect(entry.operation).toBe('create');
    expect(entry.attempts).toBe(0);
    expect(entry.id).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('flush e pullChanges sono stub senza effetti', async () => {
    expect(await service.flush()).toBe(0);
    expect(await service.pullChanges()).toBe(0);
  });
});
