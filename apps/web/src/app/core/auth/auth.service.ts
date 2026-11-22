import { Injectable, signal } from '@angular/core';

/**
 * Stato di autenticazione (stub).
 * Il token arriverà dal provider OIDC: integrazione da definire (vedi docs/decisioni).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly token = signal<string | null>(null);

  accessToken(): string | null {
    return this.token();
  }

  setAccessToken(token: string | null): void {
    this.token.set(token);
  }
}
