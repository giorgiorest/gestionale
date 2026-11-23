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
import { MatIconRegistry } from '@angular/material/icon';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { AppConfigService } from './core/config/app-config';
import { BrandingService } from './core/branding/branding.service';
import { isPackagedDesktopApp } from './core/platform';

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
      const branding = inject(BrandingService);
      // Icone Material Symbols (incluse nell'app, non scaricate da Google).
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
      // Nell'app desktop non ancora collegata a un server la configurazione manca:
      // authGuard porta alla pagina /server.
      const loaded = await config.load();
      branding.apply(loaded?.branding);
      if (loaded) {
        await auth.init();
      }
    }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideServiceWorker('ngsw-worker.js', {
      // Nell'app desktop i file sono già locali: niente service worker.
      enabled: !isDevMode() && !isPackagedDesktopApp(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
