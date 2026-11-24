import { Routes } from '@angular/router';
import { consoleAuthGuard } from './console.guard';
import { ConsoleShellComponent, ConsoleUnavailablePage } from './console-shell.component';

/** Console di piattaforma: indipendente dal login e dalla cornice dell'app delle aziende. */
export const CONSOLE_ROUTES: Routes = [
  { path: 'non-disponibile', component: ConsoleUnavailablePage },
  {
    path: '',
    canActivate: [consoleAuthGuard],
    canActivateChild: [consoleAuthGuard],
    component: ConsoleShellComponent,
    children: [
      { path: '', loadComponent: () => import('./tenants.page').then((m) => m.TenantsPage) },
      {
        path: 'aziende/:id',
        loadComponent: () => import('./tenant-detail.page').then((m) => m.TenantDetailPage),
      },
      // Ritorno dal login: lo completa la guardia, che poi apre la pagina richiesta.
      { path: 'auth/callback', redirectTo: '' },
    ],
  },
];
