import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { LocalDataService } from '../privacy/local-data.service';
import { SyncService } from '../sync/sync.service';
import { AuthService } from './auth.service';
import { LogoutService } from './logout.service';

describe('LogoutService', () => {
  let pending: number[];
  let calls: string[];
  let dialogAnswer: boolean | undefined;
  let service: LogoutService;

  beforeEach(() => {
    calls = [];
    dialogAnswer = undefined;
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { logout: async () => void calls.push('logout') } },
        { provide: SyncService, useValue: { flush: async () => (calls.push('flush'), 0) } },
        {
          provide: LocalDataService,
          useValue: {
            pendingChanges: async () => pending.shift() ?? 0,
            clear: async () => void calls.push('clear'),
          },
        },
        {
          provide: MatDialog,
          useValue: { open: () => (calls.push('dialog'), { afterClosed: () => of(dialogAnswer) }) },
        },
      ],
    });
    service = TestBed.inject(LogoutService);
  });

  it('senza modifiche in attesa cancella i dati locali ed esce', async () => {
    pending = [0];

    expect(await service.logout()).toBe(true);
    expect(calls).toEqual(['clear', 'logout']);
  });

  it('invia le modifiche in attesa prima di uscire', async () => {
    pending = [2, 0];

    expect(await service.logout()).toBe(true);
    expect(calls).toEqual(['flush', 'clear', 'logout']);
  });

  it('se restano modifiche non inviate chiede conferma; annullando non esce e non cancella', async () => {
    pending = [2, 2];
    dialogAnswer = false;

    expect(await service.logout()).toBe(false);
    expect(calls).toEqual(['flush', 'dialog']);
  });

  it('confermando cancella ed esce', async () => {
    pending = [1, 1];
    dialogAnswer = true;

    expect(await service.logout()).toBe(true);
    expect(calls).toEqual(['flush', 'dialog', 'clear', 'logout']);
  });
});
