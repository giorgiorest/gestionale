import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../api/api-url';

/** Profilo dell'utente corrente, come restituito da GET /api/me. */
export interface CurrentUser {
  userId: string;
  username: string | null;
  name: string | null;
  email: string | null;
  tenant: { id: string; name: string; kind: string; status: string } | null;
  roles: string[];
  permissions: string[];
}

/** Etichette dei ruoli applicativi. */
export const ROLE_LABELS: Record<string, string> = {
  admin: 'Amministratore',
  operator: 'Operatore',
  viewer: 'Consultazione',
  capocantiere: 'Capocantiere',
  caposquadra: 'Caposquadra',
  operaio: 'Operaio',
  amministrativo: 'Amministrativo',
};

/** Etichette dei permessi. */
export const PERMISSION_LABELS: Record<string, string> = {
  'customers:read': 'Consultare i clienti',
  'customers:write': 'Creare e modificare i clienti',
  'assistant:use': "Usare l'assistente",
  'notifications:send': 'Inviare comunicazioni ai dipendenti',
  'users:manage': 'Gestire gli utenti',
  'resources:read': 'Consultare le risorse',
  'resources:write': 'Creare e modificare le risorse',
  'commesse:read': 'Consultare le commesse',
  'commesse:write': 'Creare e modificare le commesse',
  'resource-assignments:read': 'Consultare le assegnazioni di risorse alle commesse',
  'resource-assignments:write': 'Assegnare e rimuovere risorse dalle commesse',
};

/**
 * Profilo, ruoli e permessi dell'utente.
 * Serve a mostrare o nascondere la UI: i permessi sono verificati comunque dal backend.
 */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);

  private readonly state = signal<CurrentUser | null>(null);
  private loading: Promise<CurrentUser | null> | null = null;

  readonly me = this.state.asReadonly();
  readonly permissions = computed(() => new Set(this.state()?.permissions ?? []));

  hasPermission(permission: string): boolean {
    return this.permissions().has(permission);
  }

  load(force = false): Promise<CurrentUser | null> {
    if (!this.loading || force) {
      this.loading = firstValueFrom(this.http.get<CurrentUser>(`${this.apiUrl}/me`))
        .then((value) => {
          this.state.set(value);
          return value;
        })
        .catch(() => {
          this.state.set(null);
          this.loading = null;
          return null;
        });
    }
    return this.loading;
  }
}
