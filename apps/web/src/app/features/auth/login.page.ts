import { Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IonButton, IonContent, IonHeader, IonNote, IonText, IonTitle, IonToolbar } from '@ionic/angular';
import { AuthService } from '../../core/auth/auth.service';
import { AppConfigService } from '../../core/config/app-config';

/** Pagina di accesso: rimanda al provider di identità (Keycloak). */
@Component({
  selector: 'app-login',
  imports: [IonButton, IonContent, IonHeader, IonNote, IonText, IonTitle, IonToolbar],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-title>Gestionale</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      <div class="login">
        <ion-text><h1>Benvenuto</h1></ion-text>
        <p>Accedi con il tuo account aziendale per continuare.</p>
        <ion-button expand="block" (click)="login()" [disabled]="busy()">Accedi</ion-button>
        @if (error()) {
          <ion-note color="danger">{{ error() }}</ion-note>
        }
      </div>
    </ion-content>
  `,
  styles: `
    .login {
      max-width: 360px;
      margin: 15vh auto 0;
      text-align: center;
    }
  `,
})
export class LoginPage {
  /** Pagina da riaprire dopo il login (query param). */
  readonly returnUrl = input<string>('/');

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly appConfig = inject(AppConfigService);
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
