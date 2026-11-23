import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AppConfigService } from '../config/app-config';
import { AuthService } from './auth.service';

/**
 * Rotte riservate agli utenti autenticati: gli altri vanno alla pagina di accesso.
 * Nell'app desktop non ancora collegata a un server si va prima alla scelta del server.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  if (!inject(AppConfigService).hasConfig) {
    return router.createUrlTree(['/server']);
  }
  if (inject(AuthService).isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/accesso'], { queryParams: { returnUrl: state.url } });
};
