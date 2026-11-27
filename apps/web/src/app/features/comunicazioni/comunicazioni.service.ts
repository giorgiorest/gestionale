import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { LOCAL_DB } from '../../db/db-locale';
import { uuidv7 } from '../../shared/uuid';

export type TipoPubblico = 'tenant' | 'role' | 'user';

/** Nuova comunicazione ai dipendenti. */
export interface ComunicazioneInput {
  title: string;
  body: string;
  link: string | null;
  audience: { type: TipoPubblico; value: string | null };
  requiresAck: boolean;
  sendEmail: boolean;
}

/** Comunicazione inviata, con le statistiche. */
export interface RiepilogoComunicazione {
  id: string;
  title: string;
  body: string;
  link: string | null;
  audienceType: TipoPubblico;
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
export interface StatoDestinatario {
  userId: string;
  name: string | null;
  email: string | null;
  assignedAt: string;
  readAt: string | null;
  acknowledgedAt: string | null;
  emailStatus: 'pending' | 'sent' | 'failed' | null;
}

/** Utente della rubrica del tenant. */
export interface UtenteRubrica {
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
export class ComunicazioniService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly sync = inject(SincronizzazioneService);
  private readonly db = inject(LOCAL_DB);

  /** Accoda la comunicazione nella outbox e prova a inviarla subito. */
  async send(input: ComunicazioneInput): Promise<SendResult> {
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

  list(): Promise<RiepilogoComunicazione[]> {
    return firstValueFrom(this.http.get<RiepilogoComunicazione[]>(`${this.apiUrl}/announcements`));
  }

  get(id: string): Promise<RiepilogoComunicazione> {
    return firstValueFrom(this.http.get<RiepilogoComunicazione>(`${this.apiUrl}/announcements/${id}`));
  }

  recipients(id: string): Promise<StatoDestinatario[]> {
    return firstValueFrom(this.http.get<StatoDestinatario[]>(`${this.apiUrl}/announcements/${id}/recipients`));
  }

  directory(): Promise<UtenteRubrica[]> {
    return firstValueFrom(this.http.get<UtenteRubrica[]>(`${this.apiUrl}/directory/users`));
  }
}
