import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { AnnouncementSummary, AnnouncementsService, RecipientStatus } from './announcements.service';

/** Report di una comunicazione: chi l'ha letta e chi ha confermato la presa visione. */
@Component({
  selector: 'app-announcement-report',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatTableModule],
  template: `
    <div class="page">
      <a mat-button routerLink="/comunicazioni" class="back"><mat-icon>arrow_back</mat-icon>Comunicazioni</a>
      @if (summary(); as s) {
        <div class="page-header">
          <div>
            <h1>{{ s.title }}</h1>
            <span class="subtitle">Inviata da {{ s.senderName ?? 'civil beefy' }} il {{ s.createdAt | date: 'd MMMM y, HH:mm' }}</span>
          </div>
        </div>
        <div class="cards totals" data-testid="totals">
          <mat-card appearance="outlined"><mat-card-content><span class="kpi">{{ s.recipients }}</span>Destinatari</mat-card-content></mat-card>
          <mat-card appearance="outlined"><mat-card-content><span class="kpi">{{ s.read }}</span>Lette</mat-card-content></mat-card>
          @if (s.requiresAck) {
            <mat-card appearance="outlined"><mat-card-content><span class="kpi">{{ s.acknowledged }}</span>Presa visione</mat-card-content></mat-card>
          }
          @if (s.sendEmail) {
            <mat-card appearance="outlined">
              <mat-card-content>
                <span class="kpi">{{ s.emailsSent }}</span>Email inviate
                @if (s.emailsFailed) {
                  <span class="status todo">{{ s.emailsFailed }} non consegnate</span>
                }
              </mat-card-content>
            </mat-card>
          }
        </div>
        @if (s.body) {
          <mat-card appearance="outlined" class="body"><mat-card-content>{{ s.body }}</mat-card-content></mat-card>
        }
        <mat-card appearance="outlined">
          <mat-card-content>
            <table mat-table [dataSource]="recipients()" data-testid="recipients">
              <ng-container matColumnDef="person">
                <th mat-header-cell *matHeaderCellDef>Persona</th>
                <td mat-cell *matCellDef="let r">
                  <strong>{{ r.name ?? r.userId }}</strong>
                  <div class="muted">{{ r.email ?? 'nessuna email' }}</div>
                </td>
              </ng-container>
              <ng-container matColumnDef="read">
                <th mat-header-cell *matHeaderCellDef>Letta</th>
                <td mat-cell *matCellDef="let r">{{ r.readAt ? (r.readAt | date: 'd/MM/yy HH:mm') : '—' }}</td>
              </ng-container>
              <ng-container matColumnDef="ack">
                <th mat-header-cell *matHeaderCellDef>Presa visione</th>
                <td mat-cell *matCellDef="let r">
                  @if (r.acknowledgedAt) {
                    <span class="status ok">{{ r.acknowledgedAt | date: 'd/MM/yy HH:mm' }}</span>
                  } @else {
                    <span class="status todo">Non confermata</span>
                  }
                </td>
              </ng-container>
              <ng-container matColumnDef="email">
                <th mat-header-cell *matHeaderCellDef>Email</th>
                <td mat-cell *matCellDef="let r">{{ emailLabel(r) }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columnsFor(s)"></tr>
              <tr mat-row *matRowDef="let row; columns: columnsFor(s)"></tr>
              <tr class="mat-row" *matNoDataRow>
                <td class="mat-cell empty" [attr.colspan]="columnsFor(s).length">Nessun destinatario ha ancora effettuato l'accesso.</td>
              </tr>
            </table>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: `
    .back {
      margin-bottom: 12px;
    }
    .totals {
      margin-bottom: 16px;
    }
    .kpi {
      display: block;
      font: var(--mat-sys-display-small);
      color: var(--mat-sys-primary);
    }
    .body {
      margin-bottom: 16px;
      white-space: pre-wrap;
    }
    .empty {
      padding: 16px;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class AnnouncementReportPage {
  readonly id = input.required<string>();

  private readonly service = inject(AnnouncementsService);
  readonly summary = signal<AnnouncementSummary | null>(null);
  readonly recipients = signal<RecipientStatus[]>([]);

  constructor() {
    effect(() => {
      const id = this.id();
      void this.service.get(id).then((s) => this.summary.set(s));
      void this.service.recipients(id).then((r) => this.recipients.set(r));
    });
  }

  columnsFor(s: AnnouncementSummary): string[] {
    return ['person', 'read', ...(s.requiresAck ? ['ack'] : []), ...(s.sendEmail ? ['email'] : [])];
  }

  emailLabel(r: RecipientStatus): string {
    switch (r.emailStatus) {
      case 'sent':
        return 'Inviata';
      case 'pending':
        return 'In invio';
      case 'failed':
        return 'Non consegnata';
      default:
        return r.email ? '—' : 'Nessuna email';
    }
  }
}
