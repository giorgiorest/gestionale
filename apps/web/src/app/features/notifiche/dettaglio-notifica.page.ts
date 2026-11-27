import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { NotificheService } from '../../core/notifiche/notifiche.service';
import { NotificaLocale } from '../../db/db-locale';

/** Dettaglio di una notifica: apertura = letta; presa visione esplicita se richiesta. */
@Component({
  selector: 'app-notification-detail',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule],
  template: `
    <div class="page narrow">
      <a mat-button routerLink="/notifiche" class="back"><mat-icon>arrow_back</mat-icon>Notifiche</a>
      @if (notification(); as n) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title data-testid="title">{{ n.title }}</mat-card-title>
            <mat-card-subtitle>{{ n.senderName ?? 'Civil Beefy' }} · {{ n.createdAt | date: 'd MMMM y, HH:mm' }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <p class="body" data-testid="body">{{ n.body }}</p>
            @if (n.requiresAck) {
              <div class="ack">
                @if (n.acknowledgedAt) {
                  <span class="status ok" data-testid="acknowledged">
                    <mat-icon inline>task_alt</mat-icon>
                    Presa visione confermata il {{ n.acknowledgedAt | date: 'd MMMM y, HH:mm' }}.
                  </span>
                } @else {
                  <p>Questa comunicazione richiede la tua conferma di presa visione.</p>
                }
              </div>
            }
          </mat-card-content>
          <mat-card-actions align="end">
            @if (n.link) {
              <a mat-button [routerLink]="n.link">Apri</a>
            }
            @if (n.requiresAck && !n.acknowledgedAt) {
              <button mat-flat-button (click)="acknowledge(n)" [disabled]="busy()" data-testid="ack-button">
                <mat-icon>task_alt</mat-icon>Confermo di aver letto e compreso
              </button>
            }
          </mat-card-actions>
        </mat-card>
      } @else if (loaded()) {
        <p class="muted">Notifica non trovata.</p>
      }
    </div>
  `,
  styles: `
    .narrow {
      max-width: 820px;
    }
    .back {
      margin-bottom: 12px;
    }
    .body {
      white-space: pre-wrap;
      font: var(--mat-sys-body-large);
      margin: 16px 0;
    }
    .ack {
      border-top: 1px solid var(--mat-sys-outline-variant);
      padding-top: 12px;
    }
  `,
})
export class DettaglioNotificaPage {
  readonly id = input.required<string>();

  private readonly service = inject(NotificheService);
  readonly notification = signal<NotificaLocale | null>(null);
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

  async acknowledge(n: NotificaLocale): Promise<void> {
    this.busy.set(true);
    try {
      this.notification.set(await this.service.acknowledge(n));
    } finally {
      this.busy.set(false);
    }
  }
}
