import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ConfigurazioneAppService } from '../configurazione/configurazione-app';
import { AutenticazioneService } from './autenticazione.service';

/**
 * Rotte riservate agli utenti autenticati: gli altri vanno alla pagina di accesso.
 * Nell'app desktop non ancora collegata a un server si va prima alla scelta del server.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  if (!inject(ConfigurazioneAppService).hasConfig) {
    return router.createUrlTree(['/server']);
  }
  if (inject(AutenticazioneService).isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/accesso'], { queryParams: { returnUrl: state.url } });
};
