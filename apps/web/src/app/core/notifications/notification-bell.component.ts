import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonBadge } from '@ionic/angular/ion-badge';
import { IonButton } from '@ionic/angular/ion-button';
import { NotificationsService } from './notifications.service';

/** Campanella con il numero di notifiche da leggere o da confermare. */
@Component({
  selector: 'app-notification-bell',
  imports: [RouterLink, IonBadge, IonButton],
  template: `
    <ion-button routerLink="/notifiche" aria-label="Notifiche" data-testid="bell">
      Notifiche
      @if (service.badge() > 0) {
        <ion-badge color="danger" class="badge" data-testid="bell-badge">{{ service.badge() }}</ion-badge>
      }
    </ion-button>
  `,
  styles: `
    .badge {
      margin-left: 6px;
    }
  `,
})
export class NotificationBellComponent {
  readonly service = inject(NotificationsService);

  constructor() {
    this.service.startPolling();
  }
}
