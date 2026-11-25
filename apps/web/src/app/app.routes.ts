import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { featureGuard } from './core/entitlements/feature.guard';
import { permissionGuard } from './core/profile/permission.guard';

export const routes: Routes = [
  {
    path: 'accesso',
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
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
  // Ritorno dal provider di identità: gestito da AuthService prima del routing.
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
        loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage),
      },
      {
        path: 'clienti',
        canActivate: [permissionGuard('customers:read')],
        loadComponent: () => import('./features/customers/customers.page').then((m) => m.CustomersPage),
      },
      {
        path: 'risorse',
        canActivate: [permissionGuard('resources:read')],
        loadComponent: () => import('./features/resources/resources.page').then((m) => m.ResourcesPage),
      },
      {
        path: 'notifiche',
        loadComponent: () => import('./features/notifications/notifications.page').then((m) => m.NotificationsPage),
      },
      {
        path: 'notifiche/:id',
        loadComponent: () =>
          import('./features/notifications/notification-detail.page').then((m) => m.NotificationDetailPage),
      },
      {
        path: 'comunicazioni',
        canActivate: [permissionGuard('notifications:send')],
        loadComponent: () => import('./features/announcements/announcements.page').then((m) => m.AnnouncementsPage),
      },
      {
        path: 'comunicazioni/:id',
        canActivate: [permissionGuard('notifications:send')],
        loadComponent: () =>
          import('./features/announcements/announcement-report.page').then((m) => m.AnnouncementReportPage),
      },
      {
        path: 'assistente',
        canActivate: [permissionGuard('assistant:use'), featureGuard('aiAssistant')],
        loadComponent: () => import('./features/assistant/assistant.component').then((m) => m.AssistantComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
