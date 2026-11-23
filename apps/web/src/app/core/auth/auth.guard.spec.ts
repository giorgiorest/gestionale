import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  function run(authenticated: boolean, url: string) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAuthenticated: () => authenticated } }],
    });
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  it('lascia passare gli utenti autenticati', () => {
    expect(run(true, '/clienti')).toBe(true);
  });

  it('manda gli altri alla pagina di accesso ricordando la destinazione', () => {
    const result = run(false, '/clienti') as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/accesso?returnUrl=%2Fclienti');
  });
});
