import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { ConfigurazioneAppService } from '../configurazione/configurazione-app';
import { authGuard } from './autenticazione.guard';
import { AutenticazioneService } from './autenticazione.service';

describe('authGuard', () => {
  function run(authenticated: boolean, url: string, hasConfig = true) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AutenticazioneService, useValue: { isAuthenticated: () => authenticated } },
        { provide: ConfigurazioneAppService, useValue: { hasConfig } },
      ],
    });
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  const serialize = (tree: unknown) => TestBed.inject(Router).serializeUrl(tree as UrlTree);

  it('lascia passare gli utenti autenticati', () => {
    expect(run(true, '/clienti')).toBe(true);
  });

  it('manda gli altri alla pagina di accesso ricordando la destinazione', () => {
    expect(serialize(run(false, '/clienti'))).toBe('/accesso?returnUrl=%2Fclienti');
  });

  it('nell\'app desktop senza server porta alla scelta del server', () => {
    expect(serialize(run(false, '/clienti', false))).toBe('/server');
  });
});
