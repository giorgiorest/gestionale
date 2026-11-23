import { Component, inject } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { NotificationsService } from './notifications.service';

/** Campanella con il numero di notifiche da leggere o da confermare. */
@Component({
  selector: 'app-notification-bell',
  imports: [RouterLink, MatBadgeModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <button
      mat-icon-button
      routerLink="/notifiche"
      matTooltip="Notifiche"
      aria-label="Notifiche"
      data-testid="bell"
    >
      <mat-icon
        [matBadge]="service.badge() || null"
        matBadgeColor="warn"
        matBadgeSize="medium"
        matBadgeOverlap="true"
        aria-hidden="false"
        [attr.data-badge]="service.badge()"
        >notifications</mat-icon
      >
    </button>
  `,
})
export class NotificationBellComponent {
  readonly service = inject(NotificationsService);

  constructor() {
    this.service.startPolling();
  }
}
