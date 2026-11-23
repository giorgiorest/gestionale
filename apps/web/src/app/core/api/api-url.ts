import { InjectionToken, inject } from '@angular/core';
import { AppConfigService } from '../config/app-config';

/** URL base dell'API, dalla configurazione a runtime. Sostituibile nei test. */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => inject(AppConfigService).config.apiUrl,
});
