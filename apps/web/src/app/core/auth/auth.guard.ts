import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Rotte riservate agli utenti autenticati: gli altri vanno alla pagina di accesso. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/accesso'], { queryParams: { returnUrl: state.url } });
};
