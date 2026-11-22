import { Routes } from '@angular/router';
import { featureGuard } from './core/entitlements/feature.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'assistente',
    canActivate: [featureGuard('aiAssistant')],
    loadComponent: () => import('./features/assistant/assistant.component').then((m) => m.AssistantComponent),
  },
  { path: '**', redirectTo: '' },
];
