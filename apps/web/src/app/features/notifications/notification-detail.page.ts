import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonNote,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { NotificationsService } from '../../core/notifications/notifications.service';
import { LocalNotification } from '../../db/local-db';

/** Dettaglio di una notifica: apertura = letta; presa visione esplicita se richiesta. */
@Component({
  selector: 'app-notification-detail',
  imports: [DatePipe, RouterLink, IonBackButton, IonButton, IonButtons, IonContent, IonHeader, IonNote, IonText, IonTitle, IonToolbar],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start"><ion-back-button defaultHref="/notifiche"></ion-back-button></ion-buttons>
        <ion-title>Notifica</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      @if (notification(); as n) {
        <ion-text><h1 data-testid="title">{{ n.title }}</h1></ion-text>
        <ion-note>{{ n.senderName ?? 'Gestionale' }} · {{ n.createdAt | date: 'd MMMM y, HH:mm' }}</ion-note>
        <p class="body" data-testid="body">{{ n.body }}</p>
        @if (n.link) {
          <ion-button fill="outline" [routerLink]="n.link">Apri</ion-button>
        }
        @if (n.requiresAck) {
          <div class="ack">
            @if (n.acknowledgedAt) {
              <ion-text color="success">
                <p data-testid="acknowledged">Presa visione confermata il {{ n.acknowledgedAt | date: 'd MMMM y, HH:mm' }}.</p>
              </ion-text>
            } @else {
              <p>Questa comunicazione richiede la tua conferma di presa visione.</p>
              <ion-button expand="block" (click)="acknowledge(n)" [disabled]="busy()" data-testid="ack-button">
                Confermo di aver letto e compreso
              </ion-button>
            }
          </div>
        }
      } @else if (loaded()) {
        <p>Notifica non trovata.</p>
      }
    </ion-content>
  `,
  styles: `
    .body {
      white-space: pre-wrap;
      margin: 16px 0;
    }
    .ack {
      margin-top: 24px;
      border-top: 1px solid var(--ion-color-step-150, #ddd);
      padding-top: 8px;
    }
  `,
})
export class NotificationDetailPage {
  readonly id = input.required<string>();

  private readonly service = inject(NotificationsService);
  readonly notification = signal<LocalNotification | null>(null);
  readonly loaded = signal(false);
  readonly busy = signal(false);

  constructor() {
    effect(() => {
      const id = this.id();
      void this.open(id);
    });
  }

  private async open(id: string): Promise<void> {
    const found = await this.service.get(id);
    this.loaded.set(true);
    this.notification.set(found ? await this.service.markRead(found) : null);
  }

  async acknowledge(n: LocalNotification): Promise<void> {
    this.busy.set(true);
    try {
      this.notification.set(await this.service.acknowledge(n));
    } finally {
      this.busy.set(false);
    }
  }
}
