import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonContent, IonHeader, IonItem, IonLabel, IonList, IonTitle, IonToolbar } from '@ionic/angular';
import { HasFeatureDirective } from '../../core/entitlements/has-feature.directive';

@Component({
  selector: 'app-home',
  imports: [RouterLink, HasFeatureDirective, IonContent, IonHeader, IonItem, IonLabel, IonList, IonTitle, IonToolbar],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-title>Gestionale</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      <ion-list>
        <ion-item *appHasFeature="'aiAssistant'" routerLink="/assistente" detail>
          <ion-label>Assistente</ion-label>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class HomeComponent {}
