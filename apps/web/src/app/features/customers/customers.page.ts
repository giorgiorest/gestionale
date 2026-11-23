import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonBackButton,
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonNote,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { HasPermissionDirective } from '../../core/profile/has-permission.directive';
import { SyncService } from '../../core/sync/sync.service';
import { CustomersService } from './customers.service';

/** Anagrafica clienti: elenco e inserimento rapido (funziona anche offline). */
@Component({
  selector: 'app-customers',
  imports: [
    FormsModule,
    HasPermissionDirective,
    IonBackButton,
    IonBadge,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonInput,
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
        <ion-buttons slot="start">
          <ion-back-button defaultHref="/"></ion-back-button>
        </ion-buttons>
        <ion-title>Clienti</ion-title>
        @if (sync.pending() > 0) {
          <ion-badge slot="end" color="warning" class="ion-margin-end">{{ sync.pending() }} da sincronizzare</ion-badge>
        }
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      @if (service.offline()) {
        <ion-note color="warning">Server non raggiungibile: stai vedendo la copia salvata sul dispositivo.</ion-note>
      }

      <form *appHasPermission="'customers:write'" (ngSubmit)="add()" class="ion-margin-bottom">
        <ion-list>
          <ion-item>
            <ion-input label="Ragione sociale" labelPlacement="stacked" name="name" [(ngModel)]="name"></ion-input>
          </ion-item>
          <ion-item>
            <ion-input label="Email" labelPlacement="stacked" type="email" name="email" [(ngModel)]="email"></ion-input>
          </ion-item>
        </ion-list>
        <ion-button type="submit" expand="block" [disabled]="!name.trim() || saving()">Aggiungi cliente</ion-button>
      </form>

      <ion-list data-testid="customers">
        @for (customer of service.customers(); track customer.id) {
          <ion-item>
            <ion-label>
              <h2>{{ customer.name }}</h2>
              <p>{{ customer.email || 'Email non indicata' }}</p>
            </ion-label>
          </ion-item>
        } @empty {
          <ion-item lines="none"><ion-label>Nessun cliente.</ion-label></ion-item>
        }
      </ion-list>
    </ion-content>
  `,
})
export class CustomersPage {
  readonly service = inject(CustomersService);
  readonly sync = inject(SyncService);
  readonly saving = signal(false);
  name = '';
  email = '';

  constructor() {
    void this.service.load();
  }

  async add(): Promise<void> {
    if (!this.name.trim()) {
      return;
    }
    this.saving.set(true);
    try {
      await this.service.create(this.name, this.email || null);
      this.name = '';
      this.email = '';
    } finally {
      this.saving.set(false);
    }
  }
}
