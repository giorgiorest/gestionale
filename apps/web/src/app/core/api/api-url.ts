import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

/** URL base dell'API, letto dall'environment. Sostituibile nei test. */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => environment.apiUrl,
});
