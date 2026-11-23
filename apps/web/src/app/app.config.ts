import { registerLocaleData } from '@angular/common';
import localeIt from '@angular/common/locales/it';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/provide';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { AppConfigService } from './core/config/app-config';

// Date e numeri in italiano.
registerLocaleData(localeIt);

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'it' },
    provideBrowserGlobalErrorListeners(),
    // Prima del routing: configurazione dell'installazione, poi sessione OIDC.
    provideAppInitializer(async () => {
      // inject() va chiamato prima del primo await: dopo si è fuori dal contesto di injection.
      const config = inject(AppConfigService);
      const auth = inject(AuthService);
      await config.load();
      await auth.init();
    }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideIonicAngular({}),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
