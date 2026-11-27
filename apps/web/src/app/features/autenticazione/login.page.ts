import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { AutenticazioneService } from '../../core/autenticazione/autenticazione.service';
import { BrandingService } from '../../core/branding/branding.service';
import { ConfigurazioneAppService } from '../../core/configurazione/configurazione-app';

/** Pagina di accesso: rimanda al provider di identità (Keycloak). */
@Component({
  selector: 'app-login',
  imports: [MatButtonModule, MatCardModule, MatIconModule, RouterLink],
  template: `
    <div class="centered">
      <mat-card appearance="outlined" class="card">
        <mat-card-content>
          <img [src]="branding.logoUrl()" alt="" class="logo" />
          <h1>{{ branding.name() }}</h1>
          <p class="muted">Accedi con il tuo account aziendale per continuare.</p>
          <button mat-flat-button class="full-width" (click)="login()" [disabled]="busy()">
            <mat-icon>login</mat-icon>Accedi
          </button>
          @if (error()) {
            <div class="banner error" role="alert">{{ error() }}</div>
          }
          <p class="legal">
            @if (!appConfig.desktop) {
              <a routerLink="/app-desktop">App per Windows e Android</a> ·
            }
            <a routerLink="/privacy">Privacy e cookie</a>
          </p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .card {
      width: 100%;
      max-width: 380px;
      text-align: center;
      padding: 24px 8px;
    }
    .logo {
      height: 64px;
    }
    h1 {
      font: var(--mat-sys-headline-small);
      margin: 16px 0 4px;
    }
    .banner {
      margin-top: 16px;
    }
    .legal {
      margin: 24px 0 0;
      font: var(--mat-sys-body-small);
    }
    .legal a {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class LoginPage {
  /** Pagina da riaprire dopo il login (query param). */
  readonly returnUrl = input<string>('/');

  private readonly auth = inject(AutenticazioneService);
  private readonly router = inject(Router);
  readonly appConfig = inject(ConfigurazioneAppService);
  readonly branding = inject(BrandingService);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    if (!this.appConfig.hasConfig) {
      void this.router.navigateByUrl('/server');
    } else if (this.auth.isAuthenticated()) {
      void this.router.navigateByUrl(this.returnUrl());
    }
  }

  async login(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.login(this.returnUrl() || '/');
    } catch {
      this.error.set('Servizio di accesso non raggiungibile. Riprova tra poco.');
      this.busy.set(false);
    }
  }
}
