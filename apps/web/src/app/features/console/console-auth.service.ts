import { Injectable, computed, inject, signal } from '@angular/core';
import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { AppConfigService } from '../../core/config/app-config';

/** Percorso (relativo al base href) su cui il realm degli amministratori rimanda dopo il login. */
export const CONSOLE_CALLBACK_PATH = 'console/auth/callback';

/**
 * Login degli amministratori della piattaforma: un client OIDC separato da quello dell'app
 * (realm "piattaforma", password + OTP obbligatoria). Sessione in sessionStorage con prefisso
 * proprio, indipendente da quella di un eventuale utente di un'azienda nello stesso browser.
 */
@Injectable({ providedIn: 'root' })
export class ConsoleAuthService {
  private readonly appConfig = inject(AppConfigService);
  private manager: UserManager | null = null;
  private initializing: Promise<void> | null = null;
  /** Pagina da aprire dopo il ritorno dal login: si usa una sola volta. */
  private callbackTarget: string | null = null;
  private readonly user = signal<User | null>(null);

  readonly isAuthenticated = computed(() => {
    const user = this.user();
    return !!user && !user.expired;
  });
  readonly name = computed(() => {
    const profile = this.user()?.profile;
    return profile?.name ?? profile?.preferred_username ?? '';
  });

  /** La console è disponibile solo nel browser e se l'installazione la configura. */
  available(): boolean {
    return !this.appConfig.desktop && this.appConfig.hasConfig && !!this.appConfig.config.console;
  }

  /** Inizializza il client; se l'URL è il ritorno dal login, lo completa. */
  init(): Promise<void> {
    this.initializing ??= this.doInit();
    return this.initializing;
  }

  /** Pagina richiesta prima del login, una sola volta dopo il ritorno dal provider. */
  takeCallbackTarget(): string | null {
    const target = this.callbackTarget;
    this.callbackTarget = null;
    return target;
  }

  async login(returnUrl: string): Promise<void> {
    await this.init();
    await this.requireManager().signinRedirect({ state: returnUrl });
  }

  async logout(): Promise<void> {
    await this.requireManager().signoutRedirect();
  }

  accessToken(): string | null {
    const user = this.user();
    return user && !user.expired ? user.access_token : null;
  }

  private async doInit(): Promise<void> {
    const { authority, clientId } = this.appConfig.config.console!;
    const base = new URL(document.baseURI);
    const callback = new URL(CONSOLE_CALLBACK_PATH, base);
    this.manager = new UserManager({
      authority,
      client_id: clientId,
      redirect_uri: callback.href,
      post_logout_redirect_uri: new URL('console', base).href,
      response_type: 'code',
      scope: 'openid profile email',
      automaticSilentRenew: true,
      userStore: new WebStorageStateStore({ store: window.sessionStorage, prefix: 'console.' }),
      stateStore: new WebStorageStateStore({
        store: window.sessionStorage,
        prefix: 'console.state.',
      }),
    });
    this.manager.events.addUserLoaded((user) => this.user.set(user));
    this.manager.events.addUserUnloaded(() => this.user.set(null));
    this.manager.events.addAccessTokenExpired(() => this.user.set(null));

    if (window.location.pathname === callback.pathname) {
      try {
        const user = await this.manager.signinRedirectCallback();
        this.user.set(user);
        this.callbackTarget =
          typeof user.state === 'string' && user.state.startsWith('/console')
            ? user.state
            : '/console';
      } catch (error) {
        console.error('Accesso alla console non completato', error);
        this.callbackTarget = '/console';
      }
      return;
    }
    const user = await this.manager.getUser();
    this.user.set(user && !user.expired ? user : null);
  }

  private requireManager(): UserManager {
    if (!this.manager) {
      throw new Error('ConsoleAuthService non inizializzato');
    }
    return this.manager;
  }
}
