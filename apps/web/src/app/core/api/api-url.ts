import { InjectionToken, inject } from '@angular/core';
import { ConfigurazioneAppService } from '../configurazione/configurazione-app';

/** URL base dell'API, dalla configurazione a runtime. Sostituibile nei test. */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => inject(ConfigurazioneAppService).config.apiUrl,
});
