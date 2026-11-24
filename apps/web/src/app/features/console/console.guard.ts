import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ConsoleAuthService } from './console-auth.service';

/**
 * Accesso alla console: completa il ritorno dal login o avvia il login degli amministratori.
 * È solo comodità di navigazione: la protezione vera è nell'API (/api/platform).
 */
export const consoleAuthGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(ConsoleAuthService);
  const router = inject(Router);
  if (!auth.available()) {
    return router.parseUrl('/console/non-disponibile');
  }
  await auth.init();
  const afterLogin = auth.takeCallbackTarget();
  if (afterLogin) {
    return router.parseUrl(afterLogin);
  }
  if (auth.isAuthenticated()) {
    return true;
  }
  await auth.login(state.url);
  return false;
};
