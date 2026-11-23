import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ProfileService } from './profile.service';

/** Blocca la rotta se l'utente non ha il permesso e riporta alla home. */
export function permissionGuard(permission: string): CanActivateFn {
  return async () => {
    const profile = inject(ProfileService);
    const router = inject(Router);
    await profile.load();
    return profile.hasPermission(permission) ? true : router.parseUrl('/');
  };
}
