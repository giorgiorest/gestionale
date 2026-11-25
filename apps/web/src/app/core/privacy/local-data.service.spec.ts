import { TestBed } from '@angular/core/testing';
import { LOCAL_DB, LocalDb } from '../../db/local-db';
import { LocalDataService, ownerFromAccessToken } from './local-data.service';

/** Tabella in memoria con le operazioni usate dal servizio (jsdom non ha IndexedDB). */
type Row = { key?: string; id?: string; value?: string };

function fakeTable<T extends Row = Row>() {
  const rows = new Map<string, T>();
  return {
    rows,
    get: async (key: string) => rows.get(key),
    put: async (row: T) => {
      rows.set((row.key ?? row.id)!, row);
    },
    count: async () => rows.size,
    clear: async () => rows.clear(),
  };
}

function fakeDb() {
  const db = {
    outbox: fakeTable(),
    customers: fakeTable(),
    resources: fakeTable(),
    commesse: fakeTable(),
    resourceAssignments: fakeTable(),
    workActivities: fakeTable(),
    dailyReports: fakeTable(),
    notifications: fakeTable(),
    syncState: fakeTable(),
    meta: fakeTable(),
    transaction: async (_mode: string, _tables: unknown, work: () => Promise<void>) => work(),
  };
  return db;
}

function token(claims: object): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'RS256' })}.${encode(claims)}.firma`;
}

describe('LocalDataService', () => {
  let db: ReturnType<typeof fakeDb>;
  let service: LocalDataService;

  beforeEach(() => {
    db = fakeDb();
    TestBed.configureTestingModule({
      providers: [{ provide: LOCAL_DB, useValue: db as unknown as LocalDb }],
    });
    service = TestBed.inject(LocalDataService);
  });

  it('lo stesso utente ritrova i suoi dati', async () => {
    await db.meta.put({ key: 'owner', value: 't1:mario' });
    await db.customers.put({ id: 'c1' });

    expect(await service.claim('t1:mario')).toBe(false);
    expect(db.customers.rows.size).toBe(1);
  });

  it('un altro utente sullo stesso browser non vede i dati del precedente', async () => {
    await db.meta.put({ key: 'owner', value: 't1:mario' });
    await db.customers.put({ id: 'c1' });
    await db.notifications.put({ id: 'n1' });
    await db.outbox.put({ id: 'o1' });

    expect(await service.claim('t2:anna')).toBe(true);
    expect(db.customers.rows.size).toBe(0);
    expect(db.notifications.rows.size).toBe(0);
    expect(db.outbox.rows.size).toBe(0);
    expect(db.meta.rows.get('owner')).toEqual({ key: 'owner', value: 't2:anna' });
  });

  it("i dati senza proprietario (versioni precedenti) passano all'utente corrente", async () => {
    await db.outbox.put({ id: 'o1' });

    expect(await service.claim('t1:mario')).toBe(false);
    expect(db.outbox.rows.size).toBe(1);
    expect(db.meta.rows.get('owner')?.value).toBe('t1:mario');
  });

  it('clear cancella tutto, proprietario compreso', async () => {
    await db.meta.put({ key: 'owner', value: 't1:mario' });
    await db.customers.put({ id: 'c1' });
    await db.syncState.put({ key: 'customers' });

    await service.clear();

    expect(db.customers.rows.size + db.syncState.rows.size + db.meta.rows.size).toBe(0);
  });
});

describe('ownerFromAccessToken', () => {
  it('combina tenant e utente', () => {
    expect(ownerFromAccessToken(token({ sub: 'abc', tenant_id: 't1' }))).toBe('t1:abc');
  });

  it('utente senza azienda', () => {
    expect(ownerFromAccessToken(token({ sub: 'abc' }))).toBe(':abc');
  });

  it('token non valido', () => {
    expect(ownerFromAccessToken('non-un-jwt')).toBeNull();
    expect(ownerFromAccessToken(token({ tenant_id: 't1' }))).toBeNull();
  });
});
