import { Routes } from '@angular/router';
import { authGuard } from './core/autenticazione/autenticazione.guard';
import { featureGuard } from './core/abilitazioni/funzionalita.guard';
import { permissionGuard } from './core/profilo/permesso.guard';

export const routes: Routes = [
  {
    path: 'accesso',
    loadComponent: () => import('./features/autenticazione/login.page').then((m) => m.LoginPage),
  },
  {
    // App desktop: indirizzo del server dell'azienda.
    path: 'server',
    loadComponent: () => import('./features/server/server.page').then((m) => m.ServerPage),
  },
  {
    // Informativa su cookie e dati salvati sul dispositivo: consultabile anche senza accesso.
    path: 'privacy',
    loadComponent: () => import('./features/privacy/privacy.page').then((m) => m.PrivacyPage),
  },
  {
    // Download dell'app desktop per Windows: pubblico, per chi deve ancora installarla.
    path: 'app-desktop',
    loadComponent: () =>
      import('./features/desktop/desktop-download.page').then((m) => m.DesktopDownloadPage),
  },
  {
    // Console di piattaforma: login separato (realm degli amministratori), non per i dipendenti.
    path: 'console',
    loadChildren: () => import('./features/console/console.routes').then((m) => m.CONSOLE_ROUTES),
  },
  // Ritorno dal provider di identità: gestito da AutenticazioneService prima del routing.
  { path: 'auth/callback', redirectTo: '' },
  {
    path: '',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () => import('./core/layout/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'profilo',
        loadComponent: () => import('./features/profilo/profilo.page').then((m) => m.ProfiloPage),
      },
      {
        path: 'clienti',
        canActivate: [permissionGuard('customers:read')],
        loadComponent: () => import('./features/clienti/clienti.page').then((m) => m.ClientiPage),
      },
      {
        path: 'fornitori',
        canActivate: [permissionGuard('suppliers:read')],
        loadComponent: () => import('./features/fornitori/fornitori.page').then((m) => m.FornitoriPage),
      },
      {
        path: 'risorse',
        canActivate: [permissionGuard('resources:read')],
        loadComponent: () => import('./features/risorse/risorse.page').then((m) => m.RisorsePage),
      },
      {
        path: 'commesse',
        canActivate: [permissionGuard('commesse:read')],
        loadComponent: () => import('./features/commesse/commesse.page').then((m) => m.CommessePage),
      },
      {
        path: 'notifiche',
        loadComponent: () => import('./features/notifiche/notifiche.page').then((m) => m.NotifichePage),
      },
      {
        path: 'notifiche/:id',
        loadComponent: () =>
          import('./features/notifiche/dettaglio-notifica.page').then((m) => m.DettaglioNotificaPage),
      },
      {
        path: 'comunicazioni',
        canActivate: [permissionGuard('notifications:send')],
        loadComponent: () => import('./features/comunicazioni/comunicazioni.page').then((m) => m.ComunicazioniPage),
      },
      {
        path: 'comunicazioni/:id',
        canActivate: [permissionGuard('notifications:send')],
        loadComponent: () =>
          import('./features/comunicazioni/riepilogo-comunicazione.page').then((m) => m.RiepilogoComunicazionePage),
      },
      {
        path: 'assistente',
        canActivate: [permissionGuard('assistant:use'), featureGuard('aiAssistant')],
        loadComponent: () => import('./features/assistente/assistente.component').then((m) => m.AssistenteComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
