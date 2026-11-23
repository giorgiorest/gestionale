import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonNote,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { AnnouncementSummary, AnnouncementsService, RecipientStatus } from './announcements.service';

/** Report di una comunicazione: chi l'ha letta e chi ha confermato la presa visione. */
@Component({
  selector: 'app-announcement-report',
  imports: [DatePipe, IonBackButton, IonButtons, IonContent, IonHeader, IonNote, IonText, IonTitle, IonToolbar],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start"><ion-back-button defaultHref="/comunicazioni"></ion-back-button></ion-buttons>
        <ion-title>Stato della comunicazione</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      @if (summary(); as s) {
        <ion-text><h1>{{ s.title }}</h1></ion-text>
        <ion-note>Inviata da {{ s.senderName ?? 'Gestionale' }} il {{ s.createdAt | date: 'd MMMM y, HH:mm' }}</ion-note>
        <p data-testid="totals">
          Destinatari {{ s.recipients }} · Lette {{ s.read }}
          @if (s.requiresAck) {
            · Presa visione {{ s.acknowledged }}
          }
          @if (s.sendEmail) {
            · Email inviate {{ s.emailsSent }}
            @if (s.emailsFailed) {
              (fallite {{ s.emailsFailed }})
            }
          }
        </p>
        <div class="table-wrap">
          <table data-testid="recipients">
            <thead>
              <tr>
                <th>Persona</th>
                <th>Letta</th>
                @if (s.requiresAck) {
                  <th>Presa visione</th>
                }
                @if (s.sendEmail) {
                  <th>Email</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (r of recipients(); track r.userId) {
                <tr>
                  <td>{{ r.name ?? r.userId }}<br /><small>{{ r.email ?? 'nessuna email' }}</small></td>
                  <td>{{ r.readAt ? (r.readAt | date: 'd/MM/yy HH:mm') : '—' }}</td>
                  @if (s.requiresAck) {
                    <td [class.missing]="!r.acknowledgedAt">{{ r.acknowledgedAt ? (r.acknowledgedAt | date: 'd/MM/yy HH:mm') : 'Non confermata' }}</td>
                  }
                  @if (s.sendEmail) {
                    <td>{{ emailLabel(r) }}</td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </ion-content>
  `,
  styles: `
    .table-wrap {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 8px;
      border-bottom: 1px solid var(--ion-color-step-150, #ddd);
      vertical-align: top;
    }
    .missing {
      color: var(--ion-color-danger);
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
