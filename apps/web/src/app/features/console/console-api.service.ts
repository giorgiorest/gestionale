import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { ConsoleAuthService } from './console-auth.service';

export interface ConsoleLicense {
  plan: string;
  maxUsers: number;
  features: string[];
  aiQuotaMonthly: number;
  validFrom: string;
  validUntil: string | null;
}

export interface ConsoleTenant {
  id: string;
  name: string;
  slug: string;
  kind: 'customer' | 'demo';
  status: 'active' | 'suspended' | 'expired';
  createdAt: string;
  license: ConsoleLicense | null;
}

export interface LicenzaInput {
  plan: string;
  maxUsers: number;
  features: string[];
  aiQuotaMonthly: number;
  validUntil: string | null;
}

export interface ConsoleUser {
  id: string;
  username: string;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  role: string | null;
  createdAt: string | null;
}

export interface NuovoUtenteInput {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface VoceAudit {
  id: string;
  actorName: string | null;
  action: string;
  details: Record<string, unknown>;
  createdAt: string;
}

export interface Catalog {
  features: string[];
  roles: string[];
}

/**
 * API della console di piattaforma. Usa SOLO il token degli amministratori (realm piattaforma):
 * l'interceptor dell'app non aggiunge qui il token dei dipendenti.
 * Console solo online: nessuna outbox, le modifiche vanno subito al server (ADR 0010).
 */
@Injectable({ providedIn: 'root' })
export class ConsoleApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/platform`;
  private readonly auth = inject(ConsoleAuthService);

  catalog(): Promise<Catalog> {
    return this.call(this.http.get<Catalog>(`${this.base}/catalog`, this.options()));
  }

  tenants(): Promise<ConsoleTenant[]> {
    return this.call(this.http.get<ConsoleTenant[]>(`${this.base}/tenants`, this.options()));
  }

  tenant(id: string): Promise<ConsoleTenant> {
    return this.call(this.http.get<ConsoleTenant>(`${this.base}/tenants/${id}`, this.options()));
  }

  createTenant(input: {
    id: string;
    name: string;
    slug: string;
    license: LicenzaInput;
  }): Promise<ConsoleTenant> {
    return this.call(this.http.post<ConsoleTenant>(`${this.base}/tenants`, input, this.options()));
  }

  updateTenant(
    id: string,
    input: { name: string; status: 'active' | 'suspended' },
  ): Promise<ConsoleTenant> {
    return this.call(
      this.http.put<ConsoleTenant>(`${this.base}/tenants/${id}`, input, this.options()),
    );
  }

  updateLicense(id: string, input: LicenzaInput): Promise<ConsoleTenant> {
    return this.call(
      this.http.put<ConsoleTenant>(`${this.base}/tenants/${id}/license`, input, this.options()),
    );
  }

  users(tenantId: string): Promise<ConsoleUser[]> {
    return this.call(
      this.http.get<ConsoleUser[]>(`${this.base}/tenants/${tenantId}/users`, this.options()),
    );
  }

  createUser(
    tenantId: string,
    input: NuovoUtenteInput,
  ): Promise<{ user: ConsoleUser; temporaryPassword: string }> {
    return this.call(
      this.http.post<{ user: ConsoleUser; temporaryPassword: string }>(
        `${this.base}/tenants/${tenantId}/users`,
        input,
        this.options(),
      ),
    );
  }

  updateUser(
    tenantId: string,
    userId: string,
    input: { enabled?: boolean; role?: string },
  ): Promise<ConsoleUser> {
    return this.call(
      this.http.put<ConsoleUser>(
        `${this.base}/tenants/${tenantId}/users/${userId}`,
        input,
        this.options(),
      ),
    );
  }

  resetPassword(tenantId: string, userId: string): Promise<{ temporaryPassword: string }> {
    return this.call(
      this.http.post<{ temporaryPassword: string }>(
        `${this.base}/tenants/${tenantId}/users/${userId}/password`,
        {},
        this.options(),
      ),
    );
  }

  audit(tenantId: string): Promise<VoceAudit[]> {
    return this.call(
      this.http.get<VoceAudit[]>(`${this.base}/tenants/${tenantId}/audit`, this.options()),
    );
  }

  private options(): { headers: HttpHeaders } {
    const token = this.auth.accessToken();
    return {
      headers: token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders(),
    };
  }

  private async call<T>(request: Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(request);
    } catch (error) {
      throw new Error(describeError(error));
    }
  }
}

/** Messaggio leggibile da un errore dell'API (RFC 9457 detail). */
export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Server non raggiungibile';
    }
    if (error.status === 401) {
      return 'Sessione scaduta: accedi di nuovo';
    }
    if (error.status === 403) {
      return 'Operazione non consentita';
    }
    const detail = (error.error as { detail?: string } | null)?.detail;
    return detail ?? `Errore ${error.status}`;
  }
  return error instanceof Error ? error.message : String(error);
}
