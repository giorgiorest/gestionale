import { Component, inject } from '@angular/core';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonChip,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { AuthService } from '../../core/auth/auth.service';
import { AppConfigService } from '../../core/config/app-config';
import { PERMISSION_LABELS, ProfileService, ROLE_LABELS } from '../../core/profile/profile.service';

/** Profilo utente: dati personali, azienda, ruoli e permessi. */
@Component({
  selector: 'app-profile',
  imports: [
    IonBackButton,
    IonButton,
    IonButtons,
    IonChip,
    IonContent,
    IonHeader,
    IonItem,
    IonLabel,
    IonList,
    IonListHeader,
    IonTitle,
    IonToolbar,
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button defaultHref="/"></ion-back-button>
        </ion-buttons>
        <ion-title>Profilo</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      @if (profile.me(); as me) {
        <ion-list>
          <ion-item>
            <ion-label>
              <p>Nome</p>
              <h2 data-testid="profile-name">{{ me.name || '—' }}</h2>
            </ion-label>
          </ion-item>
          <ion-item>
            <ion-label>
              <p>Utente</p>
              <h2>{{ me.username || '—' }}</h2>
            </ion-label>
          </ion-item>
          <ion-item>
            <ion-label>
              <p>Email</p>
              <h2>{{ me.email || '—' }}</h2>
            </ion-label>
          </ion-item>
          <ion-item>
            <ion-label>
              <p>Azienda</p>
              <h2>{{ me.tenant?.name ?? 'Nessuna azienda associata' }}</h2>
            </ion-label>
          </ion-item>
        </ion-list>

        <ion-list>
          <ion-list-header><ion-label>Ruoli</ion-label></ion-list-header>
          <ion-item lines="none">
            @for (role of me.roles; track role) {
              <ion-chip data-testid="role">{{ roleLabel(role) }}</ion-chip>
            } @empty {
              <ion-label>Nessun ruolo assegnato</ion-label>
            }
          </ion-item>
          <ion-list-header><ion-label>Cosa puoi fare</ion-label></ion-list-header>
          @for (permission of me.permissions; track permission) {
            <ion-item><ion-label>{{ permissionLabel(permission) }}</ion-label></ion-item>
          }
        </ion-list>
      } @else {
        <p>Profilo non disponibile.</p>
      }

      <ion-button expand="block" fill="outline" [href]="auth.accountUrl()" target="_blank">
        Gestisci account e password
      </ion-button>
      @if (appConfig.desktop) {
        <ion-item lines="none">
          <ion-label>
            <p>Server</p>
            <h2>{{ appConfig.serverUrl }}</h2>
          </ion-label>
        </ion-item>
        <ion-button expand="block" fill="clear" (click)="changeServer()">Cambia server</ion-button>
      }
      <ion-button expand="block" color="medium" (click)="auth.logout()">Esci</ion-button>
    </ion-content>
  `,
})
export class ProfilePage {
  readonly profile = inject(ProfileService);
  readonly auth = inject(AuthService);
  readonly appConfig = inject(AppConfigService);

  constructor() {
    void this.profile.load(true);
  }

  /** App desktop: dimentica server e sessione, poi torna alla scelta del server. */
  changeServer(): void {
    this.appConfig.disconnect();
    sessionStorage.clear();
    window.location.replace(new URL('server', document.baseURI).href);
  }

  roleLabel(role: string): string {
    return ROLE_LABELS[role] ?? role;
  }

  permissionLabel(permission: string): string {
    return PERMISSION_LABELS[permission] ?? permission;
  }
}
