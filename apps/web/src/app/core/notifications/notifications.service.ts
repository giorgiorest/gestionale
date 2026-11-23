import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../api/api-url';
import { ProfileService } from '../profile/profile.service';
import { SyncService } from '../sync/sync.service';
import { LOCAL_DB, LocalNotification } from '../../db/local-db';

/** Contatori della campanella. */
export interface NotificationCounts {
  unread: number;
  toAcknowledge: number;
}

/** Intervallo di aggiornamento dei contatori. */
const POLL_MS = 60_000;

/**
 * Notifiche dell'utente corrente.
 *
 * - I contatori si aggiornano ogni minuto (solo con la pagina visibile) e al ritorno della connessione.
 * - L'elenco viene salvato sul dispositivo: senza rete si vede l'ultima copia.
 * - "Letta" e "presa visione" passano dalla outbox: funzionano anche offline e vengono inviate
 *   appena possibile (PUT /api/notifications/{id}, idempotente).
 */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly db = inject(LOCAL_DB);
  private readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);

  readonly notifications = signal<LocalNotification[]>([]);
  readonly counts = signal<NotificationCounts>({ unread: 0, toAcknowledge: 0 });
  readonly badge = computed(() => Math.max(this.counts().unread, this.counts().toAcknowledge));
  readonly offline = signal(false);

  private timer: ReturnType<typeof setInterval> | null = null;

  /** Avvia l'aggiornamento periodico dei contatori (idempotente). */
  startPolling(): void {
    if (this.timer) {
      return;
    }
    // Prima il profilo: GET /api/me registra l'utente nella rubrica e, al primo accesso,
    // gli assegna le comunicazioni da confermare. Solo dopo i contatori sono corretti.
    void this.profile.load().then(() => this.refreshCounts());
    this.timer = setInterval(() => {
      if (document.visibilityState === 'visible') {
        void this.refreshCounts();
      }
    }, POLL_MS);
    window.addEventListener('online', () => void this.refreshCounts());
  }

  async refreshCounts(): Promise<void> {
    try {
      this.counts.set(await firstValueFrom(this.http.get<NotificationCounts>(`${this.apiUrl}/notifications/counts`)));
    } catch {
      // Offline o sessione scaduta: si tengono gli ultimi valori.
    }
  }

  /** Scarica l'elenco (dopo aver inviato le modifiche in sospeso) o usa la copia locale. */
  async load(): Promise<void> {
    await this.sync.flush();
    try {
      const remote = await firstValueFrom(this.http.get<LocalNotification[]>(`${this.apiUrl}/notifications`));
      await this.db.notifications.clear();
      await this.db.notifications.bulkPut(remote);
      this.offline.set(false);
      void this.refreshCounts();
    } catch {
      this.offline.set(true);
    }
    await this.refreshLocal();
  }

  async get(id: string): Promise<LocalNotification | undefined> {
    try {
      const remote = await firstValueFrom(this.http.get<LocalNotification>(`${this.apiUrl}/notifications/${id}`));
      await this.db.notifications.put(remote);
      return remote;
    } catch {
      return this.db.notifications.get(id);
    }
  }

  /** Segna come letta (se non lo è già). */
  async markRead(notification: LocalNotification): Promise<LocalNotification> {
    if (notification.readAt) {
      return notification;
    }
    return this.update(notification, { read: true }, { readAt: new Date().toISOString() });
  }

  /** Conferma la presa visione: l'ora registrata è quella del server alla ricezione. */
  async acknowledge(notification: LocalNotification): Promise<LocalNotification> {
    if (!notification.requiresAck || notification.acknowledgedAt) {
      return notification;
    }
    const now = new Date().toISOString();
    return this.update(notification, { acknowledged: true }, { acknowledgedAt: now, readAt: notification.readAt ?? now });
  }

  private async update(
    notification: LocalNotification,
    body: { read?: boolean; acknowledged?: boolean },
    local: Partial<LocalNotification>,
  ): Promise<LocalNotification> {
    const updated = { ...notification, ...local };
    await this.db.notifications.put(updated);
    await this.sync.enqueue('notifications', 'update', { id: notification.id, ...body });
    this.counts.update((c) => ({
      unread: Math.max(0, c.unread - (local.readAt && !notification.readAt ? 1 : 0)),
      toAcknowledge: Math.max(0, c.toAcknowledge - (local.acknowledgedAt ? 1 : 0)),
    }));
    await this.refreshLocal();
    void this.sync.flush().then(() => this.refreshCounts());
    return updated;
  }

  private async refreshLocal(): Promise<void> {
    const all = await this.db.notifications.toArray();
    this.notifications.set(all.sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
  }
}
