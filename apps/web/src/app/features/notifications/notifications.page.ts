import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonNote,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { NotificationsService } from '../../core/notifications/notifications.service';

/** Elenco delle notifiche dell'utente. */
@Component({
  selector: 'app-notifications',
  imports: [DatePipe, RouterLink, IonBackButton, IonButtons, IonContent, IonHeader, IonItem, IonLabel, IonList, IonNote, IonText, IonTitle, IonToolbar],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start"><ion-back-button defaultHref="/"></ion-back-button></ion-buttons>
        <ion-title>Notifiche</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      @if (service.offline()) {
        <ion-note color="warning">Server non raggiungibile: stai vedendo le notifiche salvate sul dispositivo.</ion-note>
      }
      @if (service.counts().toAcknowledge > 0) {
        <ion-text color="danger">
          <p data-testid="to-ack">
            Hai {{ service.counts().toAcknowledge }} comunicazion{{ service.counts().toAcknowledge === 1 ? 'e' : 'i' }} da confermare.
          </p>
        </ion-text>
      }
      <ion-list data-testid="notifications">
        @for (n of service.notifications(); track n.id) {
          <ion-item [routerLink]="['/notifiche', n.id]" detail>
            <ion-label>
              <h2 [class.unread]="!n.readAt">{{ n.title }}</h2>
              <p>{{ n.senderName ?? 'Gestionale' }} · {{ n.createdAt | date: 'd MMM y, HH:mm' }}</p>
            </ion-label>
            @if (n.requiresAck) {
              <ion-note slot="end" [color]="n.acknowledgedAt ? 'success' : 'danger'">
                {{ n.acknowledgedAt ? 'Confermata' : 'Da confermare' }}
              </ion-note>
            }
          </ion-item>
        } @empty {
          <ion-item lines="none"><ion-label>Nessuna notifica.</ion-label></ion-item>
        }
      </ion-list>
    </ion-content>
  `,
  styles: `
    .unread {
      font-weight: 700;
    }
  `,
})
export class NotificationsPage {
  readonly service = inject(NotificationsService);

  constructor() {
    void this.service.load();
  }
}
