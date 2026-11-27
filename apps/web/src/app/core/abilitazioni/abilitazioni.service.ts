import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../api/api-url';

/** Entitlement del tenant, come restituiti da GET /api/entitlements. */
export interface Abilitazioni {
  plan: string;
  maxUsers: number;
  features: string[];
  aiQuotaMonthly: number;
  validUntil: string | null;
}

/**
 * Carica gli entitlement del tenant corrente.
 * Serve solo a mostrare o nascondere la UI: il controllo vero è sempre nel backend.
 * Se il caricamento fallisce le funzionalità restano nascoste.
 */
@Injectable({ providedIn: 'root' })
export class AbilitazioniService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);

  private readonly state = signal<Abilitazioni | null>(null);
  private loading: Promise<Abilitazioni | null> | null = null;

  readonly entitlements = this.state.asReadonly();
  readonly features = computed(() => new Set(this.state()?.features ?? []));

  hasFeature(feature: string): boolean {
    return this.features().has(feature);
  }

  /** Carica gli entitlement una sola volta; `force` per ricaricarli. */
  load(force = false): Promise<Abilitazioni | null> {
    if (!this.loading || force) {
      this.loading = firstValueFrom(this.http.get<Abilitazioni>(`${this.apiUrl}/entitlements`))
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
