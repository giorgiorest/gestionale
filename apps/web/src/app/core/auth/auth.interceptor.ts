import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_URL } from '../api/api-url';
import { AuthService } from './auth.service';

/**
 * Aggiunge il bearer token alle sole chiamate verso la nostra API.
 * Le chiamate della console di piattaforma (/api/platform) usano il token degli amministratori,
 * aggiunto da ConsoleApiService: il token di un dipendente non viene mai inviato lì.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).accessToken();
  const apiUrl = inject(API_URL);
  if (!token || !req.url.startsWith(apiUrl) || req.url.startsWith(`${apiUrl}/platform`)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
