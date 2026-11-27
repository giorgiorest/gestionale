import { Injectable, computed, inject, signal } from '@angular/core';
import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { ConfigurazioneAppService } from '../configurazione/configurazione-app';
import { ownerFromAccessToken } from '../privacy/dati-locali.service';

/** Percorso (relativo al base href) su cui il provider OIDC rimanda dopo il login. */
export const AUTH_CALLBACK_PATH = 'auth/callback';

/**
 * Autenticazione OIDC (Authorization Code + PKCE) verso Keycloak o un altro provider compatibile.
 *
 * - Il token resta in sessionStorage (chiusa la scheda serve un nuovo login, con SSO immediato).
 * - Il rinnovo è automatico con il refresh token.
 * - L'app usa solo claim standard più `tenant_id` e `roles`: nessuna dipendenza da API di Keycloak.
 */
@Injectable({ providedIn: 'root' })
export class AutenticazioneService {
  private readonly appConfig = inject(ConfigurazioneAppService);
  private manager: UserManager | null = null;
  private readonly user = signal<User | null>(null);

  readonly isAuthenticated = computed(() => {
    const user = this.user();
    return !!user && !user.expired;
  });

  /** Inizializza il client OIDC e completa il login se l'URL è quello di ritorno dal provider. */
  async init(): Promise<void> {
    const { authority, clientId } = this.appConfig.config.auth;
    const base = new URL(document.baseURI);
    this.manager = new UserManager({
      authority,
      client_id: clientId,
      redirect_uri: new URL(AUTH_CALLBACK_PATH, base).href,
      post_logout_redirect_uri: base.href,
      response_type: 'code',
      scope: 'openid profile email',
      automaticSilentRenew: true,
      userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    });
    this.manager.events.addUserLoaded((user) => this.user.set(user));
    this.manager.events.addUserUnloaded(() => this.user.set(null));
    this.manager.events.addAccessTokenExpired(() => this.user.set(null));

    if (window.location.pathname === new URL(AUTH_CALLBACK_PATH, base).pathname) {
      let target = base.pathname;
      try {
        const user = await this.manager.signinRedirectCallback();
        this.user.set(user);
        target = typeof user.state === 'string' ? user.state : base.pathname;
      } catch (error) {
        console.error('Login non completato', error);
      }
      // Toglie code e state dall'URL prima che parta il router.
      window.history.replaceState({}, '', target);
      return;
    }

    const user = await this.manager.getUser();
    this.user.set(user && !user.expired ? user : null);
  }

  /** Avvia il login sul provider; dopo il ritorno l'utente arriva su `returnUrl`. */
  async login(returnUrl = '/'): Promise<void> {
    await this.requireManager().signinRedirect({ state: returnUrl });
  }

  async logout(): Promise<void> {
    await this.requireManager().signoutRedirect();
  }

  /** Proprietario dei dati locali (`tenant_id:sub`), letto dal token senza chiamare il server. */
  ownerKey(): string | null {
    const token = this.accessToken();
    return token ? ownerFromAccessToken(token) : null;
  }

  accessToken(): string | null {
    const user = this.user();
    return user && !user.expired ? user.access_token : null;
  }

  /** Pagina del provider dove l'utente gestisce password, MFA e dati personali. */
  accountUrl(): string {
    return `${this.appConfig.config.auth.authority.replace(/\/$/, '')}/account`;
  }

  private requireManager(): UserManager {
    if (!this.manager) {
      throw new Error('AuthService non inizializzato');
    }
    return this.manager;
  }
}
