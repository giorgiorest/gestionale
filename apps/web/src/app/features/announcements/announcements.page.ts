import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { ROLE_LABELS } from '../../core/profile/profile.service';
import { AnnouncementSummary, AnnouncementsService, AudienceType, DirectoryUser } from './announcements.service';

/** Comunicazioni ai dipendenti: nuova comunicazione ed elenco con lo stato di lettura. */
@Component({
  selector: 'app-announcements',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Comunicazioni ai dipendenti</h1>
      </div>
      <div class="grid-2">
        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title>Nuova comunicazione</mat-card-title></mat-card-header>
          <mat-card-content>
            <form (ngSubmit)="send()" class="form">
              <mat-form-field appearance="outline">
                <mat-label>Titolo</mat-label>
                <input matInput name="title" [(ngModel)]="title" maxlength="200" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Testo</mat-label>
                <textarea matInput name="body" [(ngModel)]="body" rows="5"></textarea>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Destinatari</mat-label>
                <mat-select name="audienceType" [(ngModel)]="audienceType" data-testid="audience">
                  <mat-option value="tenant">Tutta l'azienda</mat-option>
                  <mat-option value="role">Un ruolo</mat-option>
                  <mat-option value="user">Una persona</mat-option>
                </mat-select>
              </mat-form-field>
              @if (audienceType === 'role') {
                <mat-form-field appearance="outline">
                  <mat-label>Ruolo</mat-label>
                  <mat-select name="role" [(ngModel)]="role" data-testid="role">
                    @for (r of roles; track r.code) {
                      <mat-option [value]="r.code">{{ r.label }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              }
              @if (audienceType === 'user') {
                <mat-form-field appearance="outline">
                  <mat-label>Persona</mat-label>
                  <mat-select name="user" [(ngModel)]="userId" data-testid="person">
                    @for (u of users(); track u.userId) {
                      <mat-option [value]="u.userId">{{ u.name || u.username || u.userId }}</mat-option>
                    }
                  </mat-select>
                  <mat-hint>Compaiono le persone che hanno già effettuato almeno un accesso.</mat-hint>
                </mat-form-field>
              }
              <mat-checkbox name="requiresAck" [(ngModel)]="requiresAck" data-testid="requires-ack">Richiedi la presa visione</mat-checkbox>
              <mat-checkbox name="sendEmail" [(ngModel)]="sendEmail" data-testid="send-email">Invia anche per email</mat-checkbox>
              <button mat-flat-button type="submit" [disabled]="!canSend() || busy()" data-testid="send">
                <mat-icon>send</mat-icon>Invia
              </button>
              @if (message()) {
                <div
                  class="banner"
                  [class.success]="messageColor() === 'success'"
                  [class.warn]="messageColor() === 'warning'"
                  [class.error]="messageColor() === 'danger'"
                  data-testid="send-result"
                >
                  {{ message() }}
                </div>
              }
            </form>
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined">
          <mat-card-header><mat-card-title>Inviate</mat-card-title></mat-card-header>
          <mat-card-content>
            <table mat-table [dataSource]="announcements()" data-testid="announcements">
              <ng-container matColumnDef="title">
                <th mat-header-cell *matHeaderCellDef>Comunicazione</th>
                <td mat-cell *matCellDef="let a">
                  <strong>{{ a.title }}</strong>
                  <div class="muted">{{ audienceLabel(a) }} · {{ a.createdAt | date: 'd MMM y, HH:mm' }}</div>
                </td>
              </ng-container>
              <ng-container matColumnDef="read">
                <th mat-header-cell *matHeaderCellDef>Lette</th>
                <td mat-cell *matCellDef="let a">{{ a.recipients ? a.read + '/' + a.recipients : '—' }}</td>
              </ng-container>
              <ng-container matColumnDef="ack">
                <th mat-header-cell *matHeaderCellDef>Presa visione</th>
                <td mat-cell *matCellDef="let a">
                  @if (!a.requiresAck) {
                    <span class="muted">non richiesta</span>
                  } @else if (a.recipients === 0) {
                    <span class="muted">al primo accesso</span>
                  } @else {
                    <span class="status" [class.ok]="a.acknowledged === a.recipients" [class.todo]="a.acknowledged < a.recipients">
                      {{ a.acknowledged }}/{{ a.recipients }}
                    </span>
                  }
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
              <tr class="mat-row" *matNoDataRow>
                <td class="mat-cell empty" [attr.colspan]="columns.length">Nessuna comunicazione inviata.</td>
              </tr>
            </table>
            @if (hasEmptyAudience()) {
              <p class="muted note">
                «Al primo accesso»: nessun destinatario è ancora entrato nel gestionale; chi accede dopo l'invio riceve
                comunque le comunicazioni con presa visione.
              </p>
            }
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding-top: 8px;
    }
    .form button {
      margin-top: 12px;
      align-self: flex-start;
    }
    .banner {
      margin-top: 12px;
    }
    .empty {
      padding: 16px;
      color: var(--mat-sys-on-surface-variant);
    }
    .note {
      margin-top: 12px;
    }
  `,
})
export class AnnouncementsPage {
  private readonly service = inject(AnnouncementsService);
  private readonly router = inject(Router);
  readonly columns = ['title', 'read', 'ack'];

  readonly roles = Object.entries(ROLE_LABELS).map(([code, label]) => ({ code, label }));
  readonly users = signal<DirectoryUser[]>([]);
  readonly announcements = signal<AnnouncementSummary[]>([]);
  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly messageColor = signal<'success' | 'warning' | 'danger'>('success');

  title = '';
  body = '';
  audienceType: AudienceType = 'tenant';
  role = 'operator';
  userId: string | null = null;
  requiresAck = false;
  sendEmail = false;

  constructor() {
    void this.reload();
    this.service.directory().then((u) => this.users.set(u), () => this.users.set([]));
  }

  canSend(): boolean {
    return !!this.title.trim() && (this.audienceType !== 'user' || !!this.userId);
  }

  async send(): Promise<void> {
    this.busy.set(true);
    this.message.set(null);
    try {
      const value = this.audienceType === 'tenant' ? null : this.audienceType === 'role' ? this.role : this.userId;
      const result = await this.service.send({
        title: this.title.trim(),
        body: this.body.trim(),
        link: null,
        audience: { type: this.audienceType, value },
        requiresAck: this.requiresAck,
        sendEmail: this.sendEmail,
      });
      if (result.status === 'rejected') {
        this.messageColor.set('danger');
        this.message.set(`Comunicazione rifiutata dal server (${result.error}). Controlla i destinatari.`);
        return;
      }
      this.messageColor.set(result.status === 'sent' ? 'success' : 'warning');
      this.message.set(
        result.status === 'sent' ? 'Comunicazione inviata.' : 'Sei offline: la comunicazione partirà al ritorno della connessione.',
      );
      this.title = '';
      this.body = '';
      this.requiresAck = false;
      this.sendEmail = false;
      await this.reload();
    } finally {
      this.busy.set(false);
    }
  }

  open(a: AnnouncementSummary): void {
    void this.router.navigate(['/comunicazioni', a.id]);
  }

  hasEmptyAudience(): boolean {
    return this.announcements().some((a) => a.requiresAck && a.recipients === 0);
  }

  audienceLabel(a: AnnouncementSummary): string {
    if (a.audienceType === 'tenant') {
      return "Tutta l'azienda";
    }
    if (a.audienceType === 'role') {
      return `Ruolo: ${ROLE_LABELS[a.audienceValue ?? ''] ?? a.audienceValue}`;
    }
    const user = this.users().find((u) => u.userId === a.audienceValue);
    return `A: ${user?.name ?? a.audienceValue}`;
  }

  private async reload(): Promise<void> {
    try {
      this.announcements.set(await this.service.list());
    } catch {
      // offline: si tiene l'elenco attuale
    }
  }
}
