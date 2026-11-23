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

@Component({
  selector: 'app-home',
  imports: [
    RouterLink,
    HasFeatureDirective,
    HasPermissionDirective,
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
          <ion-button routerLink="/profilo">Profilo</ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      @if (profile.me(); as me) {
        <h2>Ciao, {{ me.name || me.username }}</h2>
        <ion-note>{{ me.tenant?.name ?? 'Nessuna azienda associata' }}</ion-note>
      }
      <ion-list>
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

  constructor() {
    void this.profile.load();
  }
}
