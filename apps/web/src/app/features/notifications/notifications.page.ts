import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { NotificationsService } from '../../core/notifications/notifications.service';

/** Elenco delle notifiche dell'utente. */
@Component({
  selector: 'app-notifications',
  imports: [DatePipe, RouterLink, MatCardModule, MatIconModule, MatListModule],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Notifiche</h1>
        <span class="subtitle">{{ service.counts().unread }} da leggere</span>
      </div>
      @if (service.offline()) {
        <div class="banner warn"><mat-icon>cloud_off</mat-icon>Server non raggiungibile: stai vedendo le notifiche salvate sul dispositivo.</div>
      }
      @if (service.counts().toAcknowledge > 0) {
        <div class="banner warn" data-testid="to-ack">
          <mat-icon>assignment_late</mat-icon>
          Hai {{ service.counts().toAcknowledge }} comunicazion{{ service.counts().toAcknowledge === 1 ? 'e' : 'i' }} da confermare.
        </div>
      }
      <mat-card appearance="outlined">
        <mat-nav-list data-testid="notifications">
          @for (n of service.notifications(); track n.id) {
            <a mat-list-item [routerLink]="['/notifiche', n.id]" [class.unread]="!n.readAt">
              <mat-icon matListItemIcon>{{ n.kind === 'system' ? 'info' : 'campaign' }}</mat-icon>
              <span matListItemTitle class="title">{{ n.title }}</span>
              <span matListItemLine>{{ n.senderName ?? 'Civil Beefy' }} · {{ n.createdAt | date: 'd MMM y, HH:mm' }}</span>
              @if (n.requiresAck) {
                <span matListItemMeta class="status" [class.ok]="n.acknowledgedAt" [class.todo]="!n.acknowledgedAt">
                  {{ n.acknowledgedAt ? 'Confermata' : 'Da confermare' }}
                </span>
              }
            </a>
          } @empty {
            <div class="empty muted">Nessuna notifica.</div>
          }
        </mat-nav-list>
      </mat-card>
    </div>
  `,
  styles: `
    .unread .title {
      font-weight: 700;
    }
    .unread mat-icon {
      color: var(--mat-sys-primary);
    }
    .status {
      align-self: center;
    }
    .empty {
      padding: 24px;
    }
  `,
})
export class NotificationsPage {
  readonly service = inject(NotificationsService);

  constructor() {
    void this.service.load();
  }
}
