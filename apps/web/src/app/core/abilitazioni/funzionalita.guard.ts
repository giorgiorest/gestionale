import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AbilitazioniService } from './abilitazioni.service';

/** Blocca la rotta se il piano non include la funzionalità e riporta alla home. */
export function featureGuard(feature: string): CanActivateFn {
  return async () => {
    const service = inject(AbilitazioniService);
    const router = inject(Router);
    await service.load();
    return service.hasFeature(feature) ? true : router.parseUrl('/');
  };
}
