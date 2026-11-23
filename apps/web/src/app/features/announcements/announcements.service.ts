import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { SyncService } from '../../core/sync/sync.service';
import { LOCAL_DB } from '../../db/local-db';
import { uuidv7 } from '../../shared/uuid';

export type AudienceType = 'tenant' | 'role' | 'user';

/** Nuova comunicazione ai dipendenti. */
export interface AnnouncementInput {
  title: string;
  body: string;
  link: string | null;
  audience: { type: AudienceType; value: string | null };
  requiresAck: boolean;
  sendEmail: boolean;
}

/** Comunicazione inviata, con le statistiche. */
export interface AnnouncementSummary {
  id: string;
  title: string;
  body: string;
  link: string | null;
  audienceType: AudienceType;
  audienceValue: string | null;
  requiresAck: boolean;
  sendEmail: boolean;
  senderName: string | null;
  createdAt: string;
  recipients: number;
  read: number;
  acknowledged: number;
  emailsSent: number;
  emailsFailed: number;
}

/** Stato di un destinatario (report della presa visione). */
export interface RecipientStatus {
  userId: string;
  name: string | null;
  email: string | null;
  assignedAt: string;
  readAt: string | null;
  acknowledgedAt: string | null;
  emailStatus: 'pending' | 'sent' | 'failed' | null;
}

/** Utente della rubrica del tenant. */
export interface DirectoryUser {
  userId: string;
  username: string | null;
  name: string | null;
  email: string | null;
  roles: string[];
}

/** Esito dell'invio. */
export type SendResult = { status: 'sent' } | { status: 'queued' } | { status: 'rejected'; error: string };

/** Comunicazioni ai dipendenti (permesso notifications:send). */
@Injectable({ providedIn: 'root' })
export class AnnouncementsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly sync = inject(SyncService);
  private readonly db = inject(LOCAL_DB);

  /** Accoda la comunicazione nella outbox e prova a inviarla subito. */
  async send(input: AnnouncementInput): Promise<SendResult> {
    const id = uuidv7();
    await this.sync.enqueue('announcements', 'create', { id, ...input });
    await this.sync.flush();
    const entry = (await this.db.outbox.toArray()).find((e) => (e.payload as { id: string }).id === id);
    if (!entry) {
      return { status: 'sent' };
    }
    if (entry.lastError) {
      // Rifiutata dal server (es. destinatario non valido): non ha senso ritentare.
      await this.db.outbox.delete(entry.id);
      await this.sync.refreshPending();
      return { status: 'rejected', error: entry.lastError };
    }
    return { status: 'queued' };
  }

  list(): Promise<AnnouncementSummary[]> {
    return firstValueFrom(this.http.get<AnnouncementSummary[]>(`${this.apiUrl}/announcements`));
  }

  get(id: string): Promise<AnnouncementSummary> {
    return firstValueFrom(this.http.get<AnnouncementSummary>(`${this.apiUrl}/announcements/${id}`));
  }

  recipients(id: string): Promise<RecipientStatus[]> {
    return firstValueFrom(this.http.get<RecipientStatus[]>(`${this.apiUrl}/announcements/${id}/recipients`));
  }

  directory(): Promise<DirectoryUser[]> {
    return firstValueFrom(this.http.get<DirectoryUser[]>(`${this.apiUrl}/directory/users`));
  }
}
