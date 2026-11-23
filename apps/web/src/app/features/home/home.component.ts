import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonNote,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { HasFeatureDirective } from '../../core/entitlements/has-feature.directive';
import { HasPermissionDirective } from '../../core/profile/has-permission.directive';
import { ProfileService } from '../../core/profile/profile.service';
import { NotificationBellComponent } from '../../core/notifications/notification-bell.component';
import { NotificationsService } from '../../core/notifications/notifications.service';

@Component({
  selector: 'app-home',
  imports: [
    RouterLink,
    HasFeatureDirective,
    HasPermissionDirective,
    NotificationBellComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonItem,
    IonLabel,
    IonList,
    IonNote,
    IonTitle,
    IonToolbar,
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-title>Gestionale</ion-title>
        <ion-buttons slot="end">
          <app-notification-bell></app-notification-bell>
          <ion-button routerLink="/profilo">Profilo</ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      @if (profile.me(); as me) {
        <h2>Ciao, {{ me.name || me.username }}</h2>
        <ion-note>{{ me.tenant?.name ?? 'Nessuna azienda associata' }}</ion-note>
      }
      @if (notifications.counts().toAcknowledge > 0) {
        <ion-item color="warning" routerLink="/notifiche" lines="none" class="ion-margin-top" data-testid="ack-banner">
          <ion-label>
            Hai {{ notifications.counts().toAcknowledge }} comunicazion{{ notifications.counts().toAcknowledge === 1 ? 'e' : 'i' }}
            da confermare
          </ion-label>
        </ion-item>
      }
      <ion-list>
        <ion-item routerLink="/notifiche" detail>
          <ion-label>Notifiche</ion-label>
        </ion-item>
        <ion-item *appHasPermission="'notifications:send'" routerLink="/comunicazioni" detail>
          <ion-label>Comunicazioni ai dipendenti</ion-label>
        </ion-item>
        <ion-item *appHasPermission="'customers:read'" routerLink="/clienti" detail>
          <ion-label>Clienti</ion-label>
        </ion-item>
        <ng-container *appHasPermission="'assistant:use'">
          <ion-item *appHasFeature="'aiAssistant'" routerLink="/assistente" detail>
            <ion-label>Assistente</ion-label>
          </ion-item>
        </ng-container>
        <ion-item routerLink="/profilo" detail>
          <ion-label>Profilo</ion-label>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class HomeComponent {
  readonly profile = inject(ProfileService);
  readonly notifications = inject(NotificationsService);

  constructor() {
    void this.profile.load();
  }
}
