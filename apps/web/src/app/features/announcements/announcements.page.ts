import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCheckbox,
  IonContent,
  IonHeader,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonNote,
  IonSelect,
  IonSelectOption,
  IonText,
  IonTextarea,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { ROLE_LABELS } from '../../core/profile/profile.service';
import { AnnouncementSummary, AnnouncementsService, AudienceType, DirectoryUser } from './announcements.service';

/** Comunicazioni ai dipendenti: nuova comunicazione ed elenco con lo stato di lettura. */
@Component({
  selector: 'app-announcements',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    IonBackButton,
    IonButton,
    IonButtons,
    IonCheckbox,
    IonContent,
    IonHeader,
    IonInput,
    IonItem,
    IonLabel,
    IonList,
    IonListHeader,
    IonNote,
    IonSelect,
    IonSelectOption,
    IonText,
    IonTextarea,
    IonTitle,
    IonToolbar,
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start"><ion-back-button defaultHref="/"></ion-back-button></ion-buttons>
        <ion-title>Comunicazioni</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      <form (ngSubmit)="send()">
        <ion-list>
          <ion-list-header><ion-label>Nuova comunicazione</ion-label></ion-list-header>
          <ion-item>
            <ion-input label="Titolo" labelPlacement="stacked" name="title" [(ngModel)]="title" maxlength="200"></ion-input>
          </ion-item>
          <ion-item>
            <ion-textarea label="Testo" labelPlacement="stacked" name="body" [(ngModel)]="body" [autoGrow]="true" rows="4"></ion-textarea>
          </ion-item>
          <ion-item>
            <ion-select label="Destinatari" labelPlacement="stacked" name="audienceType" [(ngModel)]="audienceType" interface="popover">
              <ion-select-option value="tenant">Tutta l'azienda</ion-select-option>
              <ion-select-option value="role">Un ruolo</ion-select-option>
              <ion-select-option value="user">Una persona</ion-select-option>
            </ion-select>
          </ion-item>
          @if (audienceType === 'role') {
            <ion-item>
              <ion-select label="Ruolo" labelPlacement="stacked" name="role" [(ngModel)]="role" interface="popover">
                @for (r of roles; track r.code) {
                  <ion-select-option [value]="r.code">{{ r.label }}</ion-select-option>
                }
              </ion-select>
            </ion-item>
          }
          @if (audienceType === 'user') {
            <ion-item>
              <ion-select label="Persona" labelPlacement="stacked" name="user" [(ngModel)]="userId" interface="popover">
                @for (u of users(); track u.userId) {
                  <ion-select-option [value]="u.userId">{{ u.name || u.username || u.userId }}</ion-select-option>
                }
              </ion-select>
            </ion-item>
            <ion-note class="hint">Compaiono le persone che hanno già effettuato almeno un accesso.</ion-note>
          }
          <ion-item>
            <ion-checkbox name="requiresAck" [(ngModel)]="requiresAck" labelPlacement="end">Richiedi la presa visione</ion-checkbox>
          </ion-item>
          <ion-item>
            <ion-checkbox name="sendEmail" [(ngModel)]="sendEmail" labelPlacement="end">Invia anche per email</ion-checkbox>
          </ion-item>
        </ion-list>
        <ion-button type="submit" expand="block" [disabled]="!canSend() || busy()" data-testid="send">Invia</ion-button>
        @if (message()) {
          <ion-text [color]="messageColor()"><p data-testid="send-result">{{ message() }}</p></ion-text>
        }
      </form>

      <ion-list data-testid="announcements">
        <ion-list-header><ion-label>Inviate</ion-label></ion-list-header>
        @for (a of announcements(); track a.id) {
          <ion-item [routerLink]="['/comunicazioni', a.id]" detail>
            <ion-label>
              <h2>{{ a.title }}</h2>
              <p>{{ audienceLabel(a) }} · {{ a.createdAt | date: 'd MMM y, HH:mm' }}</p>
              @if (a.recipients === 0) {
                <p>
                  Nessun destinatario ha ancora effettuato l'accesso{{ a.requiresAck ? ': la riceveranno al primo accesso' : '' }}.
                </p>
              } @else {
                <p>
                  Lette {{ a.read }}/{{ a.recipients }}
                  @if (a.requiresAck) {
                    · Presa visione {{ a.acknowledged }}/{{ a.recipients }}
                  }
                </p>
              }
            </ion-label>
          </ion-item>
        } @empty {
          <ion-item lines="none"><ion-label>Nessuna comunicazione inviata.</ion-label></ion-item>
        }
      </ion-list>
    </ion-content>
  `,
  styles: `
    .hint {
      display: block;
      padding: 4px 16px;
    }
  `,
})
export class AnnouncementsPage {
  private readonly service = inject(AnnouncementsService);

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
